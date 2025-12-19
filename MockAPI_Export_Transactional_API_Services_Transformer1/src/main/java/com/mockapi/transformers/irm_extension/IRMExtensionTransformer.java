package com.mockapi.transformers.irm_extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

import java.util.regex.Pattern;

public class IRMExtensionTransformer extends ResponseTransformer {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern DATE_PATTERN =
            Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");

    @Override
    public String getName() {
        return "irm-extension-transformer";
    }

    @Override
    public Response transform(Request request, Response response,
                              FileSource files, Parameters parameters) {

        try {
            JsonNode requestJson = mapper.readTree(request.getBodyAsString());
            JsonNode input = requestJson.path("service-input");

            boolean validationFailed = false;

            // -------- Mandatory Fields ----------
            String irmNumber = getText(input, "irmNumber");
            String irmADCode = getText(input, "irmADCode");
            String ieCode = getText(input, "ieCode");
            String extensionDate = getText(input, "extensionDate");
            String extensionIndicator = getText(input, "extensionIndicator");
            String recordIndicator = getText(input, "recordIndicator");

            String letterNumber = getText(input, "letterNumber");
            String letterDate = getText(input, "letterDate");

            // -------- Validations ----------
            if (isInvalid(irmNumber, 50)) validationFailed = true;
            if (isInvalid(irmADCode, 7)) validationFailed = true;
            if (isInvalid(ieCode, 10)) validationFailed = true;

            if (isInvalid(extensionIndicator, 1)) validationFailed = true;
            if (isInvalid(recordIndicator, 1)) validationFailed = true;

            if (extensionDate == null || !DATE_PATTERN.matcher(extensionDate).matches())
                validationFailed = true;

            // ---- Conditional validation ----
            if ("1".equals(extensionIndicator)) {
                if (isInvalid(letterNumber, 50)) validationFailed = true;
                if (letterDate == null || !DATE_PATTERN.matcher(letterDate).matches())
                    validationFailed = true;
            }

            // -------- Select Response JSON ----------
            String fileName = validationFailed
                    ? parameters.getString("failFile")
                    : parameters.getString("successFile");

            String body = files.getTextFileNamed(fileName)
                               .readContentsAsString();

            return Response.Builder.like(response)
                    .but()
                    .body(body)
                    .build();

        } catch (Exception e) {
            return Response.Builder.like(response)
                    .status(500)
                    .body("{\"error\":\"IRM Extension transformer error\"}")
                    .build();
        }
    }

    private boolean isInvalid(String value, int maxLen) {
        return value == null || value.isEmpty() || value.length() > maxLen;
    }

    private String getText(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText().trim() : null;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }
}
