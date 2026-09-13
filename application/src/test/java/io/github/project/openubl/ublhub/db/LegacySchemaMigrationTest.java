/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.ublhub.db;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacySchemaMigrationTest {

    @Test
    void shouldPreserveLegacyDataAndExpectedFlywayChecksums() throws Exception {
        assertFlywayChecksum("V1.0.0__QuarkusQuartzTasks.sql", 662956304);
        assertFlywayChecksum("V1.0.1__TablesDefinition.sql", 456716095);

        String jdbcUrl = "jdbc:h2:mem:legacy-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "sa");
             Statement statement = connection.createStatement()) {
            runMigration(connection, "V1.0.1__TablesDefinition.sql");
            seedLegacySchema(statement);

            // V1.0.2 uses PostgreSQL's comma-separated ADD COLUMN syntax.
            // Apply its equivalent one column at a time in this H2 test.
            statement.executeUpdate("ALTER TABLE PROJECT ADD COLUMN sunat_client_id varchar(255)");
            statement.executeUpdate("ALTER TABLE PROJECT ADD COLUMN sunat_client_secret varchar(255)");
            statement.executeUpdate("ALTER TABLE COMPANY ADD COLUMN sunat_client_id varchar(255)");
            statement.executeUpdate("ALTER TABLE COMPANY ADD COLUMN sunat_client_secret varchar(255)");
            runMigration(connection, "V1.0.3__UpgradeLegacySchema.sql");

            assertSingleRow(statement, "SELECT COUNT(*) FROM PROJECT WHERE name = 'demo'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM COMPANY "
                    + "WHERE project = 'demo' AND ruc = '20123456789'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM COMPONENT "
                    + "WHERE id = '31' AND parent_id = '30' "
                    + "AND project = 'demo' AND ruc = '20123456789'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM COMPONENT_CONFIG "
                    + "WHERE component_id = '31' AND val = 'https://example.test'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM UBL_DOCUMENT "
                    + "WHERE id = 50 AND project = 'demo'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM SUNAT_NOTE "
                    + "WHERE sunat_note_id = 50 AND val = 'accepted'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM GENERATED_ID "
                    + "WHERE id = 60 AND project = 'demo'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM PROJECT_USER "
                    + "WHERE project = 'demo' AND username = 'admin' AND roles = 'owner'");
            assertSingleRow(statement, "SELECT COUNT(*) FROM PROJECT_LEGACY_V103 "
                    + "WHERE id = 10");
        }
    }

    private void seedLegacySchema(Statement statement) throws Exception {
        statement.executeUpdate("INSERT INTO APP_USER "
                + "(id, full_name, username, password, permissions, version) "
                + "VALUES (1, 'Dev Admin', 'admin', 'legacy-hash', "
                + "'admin:app,project:write', 0)");
        statement.executeUpdate("INSERT INTO PROJECT "
                + "(id, name, description, sunat_username, sunat_password, "
                + "sunat_url_factura, sunat_url_guia_remision, "
                + "sunat_url_percepcion_retencion, created, version) "
                + "VALUES (10, 'demo', 'Demo project', 'user', 'pass', "
                + "'factura', 'guia', 'percepcion', CURRENT_TIMESTAMP, 0)");
        statement.executeUpdate("INSERT INTO COMPANY "
                + "(id, ruc, name, project_id, created, version) "
                + "VALUES (20, '20123456789', 'Demo company', 10, CURRENT_TIMESTAMP, 0)");
        statement.executeUpdate("INSERT INTO COMPONENT "
                + "(id, name, provider_type, project_id) "
                + "VALUES (30, 'Project component', 'test', 10)");
        statement.executeUpdate("INSERT INTO COMPONENT "
                + "(id, name, parent_id, provider_type, company_id) "
                + "VALUES (31, 'Company component', 30, 'test', 20)");
        statement.executeUpdate("INSERT INTO COMPONENT_CONFIG "
                + "(id, name, value, component_id) "
                + "VALUES (40, 'endpoint', 'https://example.test', 31)");
        statement.executeUpdate("INSERT INTO UBL_DOCUMENT "
                + "(id, job_in_progress, xml_file_id, project_id, created, version) "
                + "VALUES (50, 'N', 'xml-50', 10, CURRENT_TIMESTAMP, 0)");
        statement.executeUpdate("INSERT INTO SUNAT_NOTE (sunat_note_id, value) "
                + "VALUES (50, 'accepted')");
        statement.executeUpdate("INSERT INTO GENERATED_ID "
                + "(id, ruc, document_type, serie, numero, project_id, created, version) "
                + "VALUES (60, '20123456789', '01', 1, 7, 10, CURRENT_TIMESTAMP, 0)");
    }

    private void runMigration(Connection connection, String filename) throws Exception {
        try (Reader reader = new InputStreamReader(resource(filename), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }

    private void assertSingleRow(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            assertEquals(1, result.getInt(1), sql);
        }
    }

    private void assertFlywayChecksum(String filename, int expected) throws Exception {
        CRC32 crc32 = new CRC32();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource(filename), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                crc32.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        assertEquals(expected, (int) crc32.getValue(), filename);
    }

    private InputStream resource(String filename) {
        InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("db/migration/" + filename);
        assertNotNull(stream, filename);
        return stream;
    }
}
