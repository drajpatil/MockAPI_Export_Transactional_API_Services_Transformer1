package com.mockapi.transformers.shipping_bill_extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

public class ShippingBillExtensionTransformer extends ResponseTransformer {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "shipping-bill-extension-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public Response transform(
            Request request,
            Response response,
            FileSource files,
            Parameters parameters) {

        try {
            JsonNode root = mapper.readTree(request.getBodyAsString());
            JsonNode input = root.path("service-input");

            boolean validationFailed = false;

            // Mandatory + Length validations
            validationFailed |= invalid(input, "exportType", 1);
            validationFailed |= invalid(input, "portCode", 6);
            validationFailed |= invalid(input, "shippingBillNo", 7);
            validationFailed |= invalid(input, "shippingBillDate", 10);
            validationFailed |= invalid(input, "leoDate", 10);
            validationFailed |= invalid(input, "adCode", 7);
            validationFailed |= invalid(input, "ieCode", 10);
            validationFailed |= invalid(input, "recordIndicator", 1);
            validationFailed |= invalid(input, "extensionBy", 1);
            validationFailed |= invalid(input, "extensionDate", 10);
            validationFailed |= invalid(input, "letterNumber", 10);
            validationFailed |= invalid(input, "letterDate", 10);

            // Softex conditional validation
            String exportType = input.path("exportType").asText();
            if ("2".equals(exportType)) { // Softex
                validationFailed |= invalid(input, "formNo", 20);
            }

            String responseFile = validationFailed
                    ? parameters.getString("failFile")
                    : parameters.getString("successFile");

            String body = files.getTextFileNamed(responseFile).readContentsAsString();

            return Response.Builder.like(response)
                    .but()
                    .body(body)
                    .build();

        } catch (Exception e) {
            return Response.Builder.like(response)
                    .but()
                    .status(500)
                    .body("{\"error\":\"Transformer Error\"}")
                    .build();
        }
    }

    private boolean invalid(JsonNode node, String field, int length) {
        if (!node.has(field)) return true;
        String value = node.path(field).asText();
        return value == null || value.trim().isEmpty() || value.length() != length;
    }
}
