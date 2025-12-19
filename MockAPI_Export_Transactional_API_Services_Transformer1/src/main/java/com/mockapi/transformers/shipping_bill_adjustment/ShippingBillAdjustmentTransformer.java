package com.mockapi.transformers.shipping_bill_adjustment;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;

public class ShippingBillAdjustmentTransformer extends ResponseDefinitionTransformer {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "shipping-bill-adjustment-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(
            Request request,
            ResponseDefinition responseDefinition,
            FileSource files,
            Parameters parameters) {

        try {
            String successFile = parameters.getString("successFile");
            String failFile = parameters.getString("failFile");

            JsonNode root = mapper.readTree(request.getBodyAsString());
            JsonNode input = root.path("service-input");
            JsonNode invoiceList = input.path("invoiceList");

            /* -------------------------------------------------
               HEADER / SERVICE-INPUT VALIDATION
            ------------------------------------------------- */

            boolean headerValid =
                    isValid(input, "exportType", 1, 1) &&                 // M
                    isValid(input, "portCode", 6, 6) &&                  // M
                    isValid(input, "leoDate", 10, 10) &&                 // M
                    isValid(input, "adCode", 7, 7) &&                    // M
                    isValid(input, "recordIndicator", 1, 1) &&           // M
                    isValid(input, "ieCode", 10, 10) &&                  // M
                    isValid(input, "writeoffReferenceNumber", 1, 30) &&  // M
                    isValid(input, "adjustmentIndicator", 1, 2) &&       // M
                    isValid(input, "writeOffDate", 10, 10) &&            // M
                    isValid(input, "shipmentInd", 1, 2);                 // M

            /* -------------------------------------------------
               CONDITIONAL VALIDATION
               exportType = 1 → GOODS
               exportType = 2 → SOFTEX
            ------------------------------------------------- */

            String exportType = input.path("exportType").asText();

            boolean exportSpecificValid;

            if ("1".equals(exportType)) { // GOODS
                exportSpecificValid =
                        isValid(input, "shippingBillNo", 7, 7) &&
                        isValid(input, "shippingBillDate", 10, 10);
            } else if ("2".equals(exportType)) { // SOFTEX
                exportSpecificValid =
                        isValid(input, "formNo", 1, 20);
            } else {
                exportSpecificValid = false;
            }

            /* -------------------------------------------------
               OPTIONAL RE-IMPORT / RE-EXPORT FIELDS
            ------------------------------------------------- */
            boolean optionalValid =
                    isOptionalValid(input, "billOfEntryNumber", 7) &&
                    isOptionalValid(input, "billOfEntryDate", 10) &&
                    isOptionalValid(input, "portOfDischarge", 6);

            /* -------------------------------------------------
               INVOICE VALIDATION (MANDATORY)
            ------------------------------------------------- */
            boolean invoiceValid = false;

            if (invoiceList.isArray() && invoiceList.size() > 0) {
                JsonNode inv = invoiceList.get(0);

                invoiceValid =
                        isValid(inv, "invoiceSerialNo", 1, 10) &&         // M
                        isValid(inv, "invoiceNumber", 1, 10) &&           // M
                        isValid(inv, "invoiceDate", 10, 10) &&            // M
                        isValid(inv, "writeoffAmount", 1, 20) &&          // M (16,4)
                        isValid(inv, "invoiceClosureIndicator", 1, 1);    // M
            }

            boolean isValidRequest =
                    headerValid &&
                    exportSpecificValid &&
                    optionalValid &&
                    invoiceValid;

            String fileToReturn = isValidRequest ? successFile : failFile;

            String body = new String(
                    Files.readAllBytes(Paths.get("__files/" + fileToReturn))
            );

            return ResponseDefinitionBuilder
                    .like(responseDefinition)
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseDefinitionBuilder
                    .like(responseDefinition)
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"Internal Server Error\"}")
                    .build();
        }
    }

    /* -------------------------------------------------
       HELPER METHODS
    ------------------------------------------------- */

    private boolean isValid(JsonNode node, String field, int min, int max) {
        if (!node.has(field) || node.get(field).isNull()) return false;
        String value = node.get(field).asText().trim();
        return !value.isEmpty() && value.length() >= min && value.length() <= max;
    }

    private boolean isOptionalValid(JsonNode node, String field, int max) {
        if (!node.has(field) || node.get(field).isNull()) return true;
        String value = node.get(field).asText().trim();
        return value.isEmpty() || value.length() <= max;
    }
}
