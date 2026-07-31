package io.github.project.openubl.ublhub.ubl.sender;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DespatchAdviceXmlTest {

    @Test
    void extractsSupplierRucFromUnsignedGre() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<DespatchAdvice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cac:DespatchSupplierParty><cac:Party><cac:PartyIdentification>"
                + "<cbc:ID schemeID=\"6\">10432220899</cbc:ID>"
                + "</cac:PartyIdentification></cac:Party></cac:DespatchSupplierParty>"
                + "</DespatchAdvice>";

        String result = XMLSenderManager.extractDespatchSupplierRuc(
                xml.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("10432220899", result);
    }
}
