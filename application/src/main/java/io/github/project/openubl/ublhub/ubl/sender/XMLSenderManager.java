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
package io.github.project.openubl.ublhub.ubl.sender;

import io.github.project.openubl.ublhub.models.jpa.CompanyRepository;
import io.github.project.openubl.ublhub.models.jpa.ProjectRepository;
import io.github.project.openubl.ublhub.models.jpa.entities.CompanyEntity;
import io.github.project.openubl.ublhub.models.jpa.entities.SunatEntity;
import io.github.project.openubl.ublhub.ubl.sender.exceptions.ConnectToSUNATException;
import io.github.project.openubl.ublhub.ubl.sender.exceptions.ReadXMLFileContentException;
import io.github.project.openubl.xsender.Constants;
import io.github.project.openubl.xsender.camel.utils.CamelData;
import io.github.project.openubl.xsender.camel.utils.CamelUtils;
import io.github.project.openubl.xsender.company.CompanyCredentials;
import io.github.project.openubl.xsender.company.CompanyURLs;
import io.github.project.openubl.xsender.files.BillServiceFileAnalyzer;
import io.github.project.openubl.xsender.files.BillServiceXMLFileAnalyzer;
import io.github.project.openubl.xsender.files.ZipFile;
import io.github.project.openubl.xsender.files.exceptions.UnsupportedXMLFileException;
import io.github.project.openubl.xsender.files.xml.DocumentType;
import io.github.project.openubl.xsender.files.xml.XmlContent;
import io.github.project.openubl.xsender.files.xml.XmlContentProvider;
import io.github.project.openubl.xsender.models.Metadata;
import io.github.project.openubl.xsender.models.Status;
import io.github.project.openubl.xsender.models.Sunat;
import io.github.project.openubl.xsender.models.SunatResponse;
import io.github.project.openubl.xsender.sunat.BillServiceDestination;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.github.project.openubl.xsender.camel.utils.CamelUtils.getBillServiceCamelData;

@Dependent
public class XMLSenderManager {

    private static final Logger LOGGER = Logger.getLogger(XMLSenderManager.class);

    @Inject
    CompanyRepository companyRepository;

    @Inject
    ProjectRepository projectRepository;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    SunatGreRestClient sunatGreRestClient;

    static final List<String> validDocumentTypes = Arrays.asList(
            DocumentType.INVOICE,
            DocumentType.CREDIT_NOTE,
            DocumentType.DEBIT_NOTE,
            DocumentType.VOIDED_DOCUMENT,
            DocumentType.SUMMARY_DOCUMENT,
            DocumentType.PERCEPTION,
            DocumentType.RETENTION,
            DocumentType.DESPATCH_ADVICE
    );

    public XmlContent getXMLContent(byte[] file) throws ReadXMLFileContentException {
        if (file == null) {
            throw new ReadXMLFileContentException("Null files can not be read");
        }

        try {
            XmlContent content = XmlContentProvider.getSunatDocument(new ByteArrayInputStream(file));
            boolean isValidDocumentType = validDocumentTypes.stream().anyMatch(s -> s.equals(content.getDocumentType()));
            if (isValidDocumentType) {
                return content;
            } else {
                throw new ReadXMLFileContentException("Invalid document type=" + content.getDocumentType());
            }
        } catch (Throwable e) {
            LOGGER.error(e);
            throw new ReadXMLFileContentException(e);
        }
    }

    public XMLSenderConfig getXSenderConfig(Long projectId, String ruc) {
        CompanyEntity companyEntity = companyRepository.findByRuc(projectId, ruc);

        SunatEntity sunatEntity;
        if (companyEntity != null) {
            sunatEntity = companyEntity.getSunat();
        } else {
            sunatEntity = projectRepository.findById(projectId).getSunat();
        }

        return XMLSenderConfig.builder()
                .facturaUrl(sunatEntity.getSunatUrlFactura())
                .guiaRemisionUrl(sunatEntity.getSunatUrlGuiaRemision())
                .percepcionRetencionUrl(sunatEntity.getSunatUrlPercepcionRetencion())
                .username(sunatEntity.getSunatUsername())
                .password(sunatEntity.getSunatPassword())
                .clientId(sunatEntity.getSunatClientId())
                .clientSecret(sunatEntity.getSunatClientSecret())
                .build();
    }

    public SunatResponse sendToSUNAT(byte[] file, XMLSenderConfig wsConfig) throws ConnectToSUNATException {
        try {
            XmlContent xmlContent = getXMLContent(file);
            if (isGreRest(xmlContent, wsConfig)) {
                return toSunatResponse(sunatGreRestClient.submit(
                        file,
                        xmlContent.getRuc(),
                        xmlContent.getDocumentID(),
                        wsConfig
                ));
            }
        } catch (ReadXMLFileContentException e) {
            throw new ConnectToSUNATException(e.getMessage());
        } catch (Throwable e) {
            throw new ConnectToSUNATException(errorMessage(e, "Could not send GRE to SUNAT"));
        }

        CompanyURLs urls = CompanyURLs.builder()
                .invoice(wsConfig.getFacturaUrl())
                .perceptionRetention(wsConfig.getPercepcionRetencionUrl())
                .despatch(wsConfig.getGuiaRemisionUrl())
                .build();
        CompanyCredentials credentials = CompanyCredentials.builder()
                .username(wsConfig.getUsername())
                .password(wsConfig.getPassword())
                .build();

        try {
            BillServiceFileAnalyzer fileAnalyzer = new BillServiceXMLFileAnalyzer(file, urls);

            ZipFile zipFile = fileAnalyzer.getZipFile();
            BillServiceDestination fileDestination = fileAnalyzer.getSendFileDestination();
            CamelData camelFileData = getBillServiceCamelData(zipFile, fileDestination, credentials);

            return producerTemplate
                    .requestBodyAndHeaders(Constants.XSENDER_BILL_SERVICE_URI, camelFileData.getBody(), camelFileData.getHeaders(), SunatResponse.class);
        } catch (ParserConfigurationException | IOException | UnsupportedXMLFileException | SAXException e) {
            return SunatResponse.builder()
                    .status(Status.RECHAZADO)
                    .metadata(Metadata.builder()
                            .description(e.getMessage())
                            .build()
                    )
                    .build();
        } catch (Throwable e) {
            // Should retry
            throw new ConnectToSUNATException("Could not send file");
        }
    }

    public SunatResponse verifyTicketAtSUNAT(
            String ticket,
            XmlContent xmlContent,
            XMLSenderConfig wsConfig
    ) throws ConnectToSUNATException {
        if (isGreRest(xmlContent, wsConfig)) {
            try {
                return toSunatResponse(sunatGreRestClient.verify(
                        ticket,
                        xmlContent.getRuc(),
                        wsConfig
                ));
            } catch (Throwable e) {
                throw new ConnectToSUNATException(
                        errorMessage(e, "Could not verify GRE ticket at SUNAT")
                );
            }
        }

        CompanyURLs urls = CompanyURLs.builder()
                .invoice(wsConfig.getFacturaUrl())
                .perceptionRetention(wsConfig.getPercepcionRetencionUrl())
                .despatch(wsConfig.getGuiaRemisionUrl())
                .build();
        CompanyCredentials credentials = CompanyCredentials.builder()
                .username(wsConfig.getUsername())
                .password(wsConfig.getPassword())
                .build();
        try {
            BillServiceDestination ticketDestination = BillServiceXMLFileAnalyzer.getTicketDeliveryTarget(urls, xmlContent).orElseThrow(IllegalStateException::new);
            CamelData camelTicketData = CamelUtils.getBillServiceCamelData(ticket, ticketDestination, credentials);

            return producerTemplate
                    .requestBodyAndHeaders(Constants.XSENDER_BILL_SERVICE_URI, camelTicketData.getBody(), camelTicketData.getHeaders(), SunatResponse.class);
        } catch (Throwable e) {
            throw new ConnectToSUNATException("Could not verify ticket");
        }
    }

    static boolean isGreRest(XmlContent xmlContent, XMLSenderConfig config) {
        return xmlContent != null
                && DocumentType.DESPATCH_ADVICE.equals(xmlContent.getDocumentType())
                && config != null
                && config.getGuiaRemisionUrl() != null
                && config.getGuiaRemisionUrl().contains("/v1/contribuyente/gem/comprobantes");
    }

    static SunatResponse toSunatResponse(SunatGreRestClient.GreResponse response) {
        int code = 0;
        if (response.errorCode() != null) {
            try {
                code = Integer.parseInt(response.errorCode());
            } catch (NumberFormatException ignored) {
                code = -1;
            }
        }
        Status status;
        switch (response.state()) {
            case PENDING:
                status = Status.UNKNOWN;
                break;
            case ACCEPTED:
                status = statusNamed("ACEPTADO");
                break;
            case REJECTED:
                status = statusNamed("RECHAZADO");
                break;
            default:
                status = Status.UNKNOWN;
        }
        return SunatResponse.builder()
                .status(status)
                .sunat(Sunat.builder()
                        .ticket(response.ticket())
                        .cdr(response.cdr())
                        .build())
                .metadata(Metadata.builder()
                        .responseCode(code)
                        .description(response.description())
                        .notes(Collections.emptyList())
                        .build())
                .build();
    }

    private static Status statusNamed(String name) {
        try {
            return Status.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return Status.UNKNOWN;
        }
    }

    private static String errorMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return fallback;
    }
}
