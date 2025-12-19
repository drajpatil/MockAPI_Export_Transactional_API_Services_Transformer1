package com.mockapi.transformers.irm_adjustment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;

public class IRMAdjustmentTransformer extends ResponseDefinitionTransformer {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "irm-adjustment-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(Request request,
                                        ResponseDefinition responseDefinition,
                                        FileSource files,
                                        Parameters parameters) {

        try {
            String successFile = parameters.getString("successFile");
            String failFile = parameters.getString("failFile");

            JsonNode root = mapper.readTree(request.getBodyAsString());
            JsonNode serviceInput = root.path("service-input");

            // -------------------------------
            // MANDATORY FIELD VALIDATION
            // -------------------------------
            boolean mandatoryValid =
                    isValid(serviceInput, "irmNumber", 1, 30) &&
                    isValid(serviceInput, "remittanceAdCode", 1, 7) &&
                    isValid(serviceInput, "ieCode", 1, 10) &&
                    isValid(serviceInput, "adjustmentSeqNumber", 1, 50) &&
                    isValid(serviceInput, "reasonForAdjustment", 1, 2) &&
                    isValid(serviceInput, "adjustedAmount", 1, 16) &&
                    isValid(serviceInput, "remitterCurrency", 1, 3) &&
                    isValid(serviceInput, "adjustmentDate", 10, 10) &&
                    isValid(serviceInput, "approvalBy", 1, 1) &&
                    isValid(serviceInput, "recordIndicator", 1, 1);

            // Optional fields: letterNo, docNumber, docDate, docPort
            boolean optionalValid =
                    isValidOptional(serviceInput, "letterNo", 10) &&
                    isValidOptional(serviceInput, "docNumber", 10) &&
                    isValidOptional(serviceInput, "docDate", 10) &&
                    isValidOptional(serviceInput, "docPort", 6);

            boolean isValidRequest = mandatoryValid && optionalValid;

            // -------------------------------
            // PICK TEMPLATE JSON FILE
            // -------------------------------
            String fileToReturn = isValidRequest ? successFile : failFile;
            String body = new String(Files.readAllBytes(Paths.get("__files/" + fileToReturn)));

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
                    .withBody("{\"error\":\"Internal Transformer Error\"}")
                    .build();
        }
    }

    // -------------------------------
    // HELPER METHODS
    // -------------------------------
    private boolean isValid(JsonNode node, String field, int min, int max) {
        if (!node.has(field) || node.get(field).isNull()) return false;
        String value = node.get(field).asText().trim();
        return !value.isEmpty() && value.length() >= min && value.length() <= max;
    }

    private boolean isValidOptional(JsonNode node, String field, int max) {
        if (!node.has(field) || node.get(field).isNull()) return true;
        String value = node.get(field).asText().trim();
        return value.isEmpty() || value.length() <= max;
    }
}
