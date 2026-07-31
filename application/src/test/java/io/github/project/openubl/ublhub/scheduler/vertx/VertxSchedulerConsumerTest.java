package io.github.project.openubl.ublhub.scheduler.vertx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxSchedulerConsumerTest {

    @Test
    void preservesUsefulErrorAndMasksSecrets() {
        String description = VertxSchedulerConsumer.safeErrorDescription(
                "No se pudo enviar el XML a la SUNAT",
                new IllegalStateException(
                        "SUNAT GRE HTTP 401: invalid_grant password=secret access_token=token"
                )
        );

        assertTrue(description.contains("SUNAT GRE HTTP 401: invalid_grant"));
        assertFalse(description.contains("password=secret"));
        assertFalse(description.contains("access_token=token"));
    }

    @Test
    void limitsDescriptionToExistingDatabaseColumnSize() {
        String description = VertxSchedulerConsumer.safeErrorDescription(
                "No se pudo enviar el XML a la SUNAT",
                new IllegalStateException("x".repeat(500))
        );

        assertEquals(255, description.length());
    }
}
