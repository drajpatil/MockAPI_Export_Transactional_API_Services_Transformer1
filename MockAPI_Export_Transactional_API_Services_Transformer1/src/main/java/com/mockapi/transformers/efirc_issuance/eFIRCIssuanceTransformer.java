package com.mockapi.transformers.efirc_issuance;

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

public class eFIRCIssuanceTransformer extends ResponseDefinitionTransformer {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "efirc-issuance-transformer";
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

            JsonNode requestJson = mapper.readTree(request.getBodyAsString());
            JsonNode input = requestJson.path("service-input");

            // ---------------- Mandatory + Length Validation ----------------
            boolean isValid =
                    isValid(input, "irmNumber", 1, 30) &&
                    isValid(input, "irmAdCode", 7, 7) &&
                    isValid(input, "fircFlag", 1, 1) &&
                    isValid(input, "fircNumber", 1, 30) &&
                    isValid(input, "fircIssueDate", 10, 10) &&
                    isValid(input, "fircAmount", 1, 20) &&
                    isValid(input, "recordIndicator", 1, 1);

            String fileToReturn = isValid ? successFile : failFile;

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

    private boolean isValid(JsonNode node, String field, int min, int max) {
        if (!node.has(field) || node.get(field).isNull()) return false;

        String value = node.get(field).asText().trim();
        return !value.isEmpty() && value.length() >= min && value.length() <= max;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }
}
