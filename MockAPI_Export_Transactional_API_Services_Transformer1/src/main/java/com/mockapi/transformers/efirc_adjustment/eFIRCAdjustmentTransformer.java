package com.mockapi.transformers.efirc_adjustment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class eFIRCAdjustmentTransformer extends ResponseTransformer {

    private static final Logger logger =
            LogManager.getLogger(eFIRCAdjustmentTransformer.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "efirc-adjustment-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public Response transform(Request request,
                              Response response,
                              FileSource files,
                              Parameters parameters) {

        try {
            // ---------- Parse Request ----------
            JsonNode requestJson =
                    mapper.readTree(request.getBodyAsString());

            JsonNode requestHeader =
                    requestJson.path("request-header");

            JsonNode serviceInput =
                    requestJson.path("service-input");

            boolean validationFailed = false;
            StringBuilder errorCode = new StringBuilder();
            StringBuilder errorDesc = new StringBuilder();

            // ---------- Mandatory + Length Validation ----------
            validationFailed |= validateField(serviceInput.path("fircNumber").asText(null),
                    50, true, "EF_HDR_001", "FIRC Number", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("adCode").asText(null),
                    7, true, "EF_HDR_002", "AD Code", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("remitterCurrency").asText(null),
                    3, true, "EF_HDR_003", "Closure Currency", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("adjustedAmount").asText(null),
                    16, true, "EF_HDR_004", "Closure Amount", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("approvalBy").asText(null),
                    1, true, "EF_HDR_005", "Approval By", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("adjustmentDate").asText(null),
                    10, true, "ED_HDR_087", "Adjustment Date", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("reasonForAdjustment").asText(null),
                    1, true, "EF_HDR_006", "Reason For Adjustment", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("adjustmentSeqNumber").asText(null),
                    50, true, "EF_HDR_007", "Closure Sequence Number", errorCode, errorDesc);

            validationFailed |= validateField(serviceInput.path("recordIndicator").asText(null),
                    1, true, "EF_HDR_008", "Record Indicator", errorCode, errorDesc);

            // ---------- Choose Response Template ----------
            String responseFile = validationFailed
                    ? parameters.getString("failFile")
                    : parameters.getString("successFile");

            ObjectNode root = (ObjectNode) mapper.readTree(
                    files.getTextFileNamed(responseFile)
                            .readContentsAsString()
            );

            // ---------- Map Request Header → Response Header ----------
            ObjectNode responseHeader =
                    (ObjectNode) root.get("response-header");

            responseHeader.put("req-hdr-request-id",
                    requestHeader.path("request-id").asText());

            responseHeader.put("req-hdr-service-name",
                    requestHeader.path("service-name").asText());

            responseHeader.put("req-hdr-request-time",
                    requestHeader.path("request-time").asText());

            responseHeader.put("request-source",
                    requestHeader.path("request-source").asText());

            // ---------- Failure Handling ----------
            if (validationFailed) {
                ObjectNode serviceOutput =
                        (ObjectNode) root.get("service-output");

                serviceOutput.put("error-code",
                        trimComma(errorCode.toString()));

                serviceOutput.put("error-desc",
                        errorDesc.toString().trim());
            }

            return Response.Builder.like(response)
                    .but()
                    .body(root.toString())
                    .build();

        } catch (Exception e) {
            logger.error("eFIRC Adjustment Transformer Error", e);
            return Response.Builder.like(response)
                    .status(500)
                    .body("{\"error\":\"Internal transformer error\"}")
                    .build();
        }
    }

    // ================= Helper Methods =================

    private boolean validateField(String value,
                                  int maxLength,
                                  boolean mandatory,
                                  String code,
                                  String fieldName,
                                  StringBuilder errorCode,
                                  StringBuilder errorDesc) {

        boolean failed = false;

        if (mandatory && (value == null || value.trim().isEmpty())) {
            failed = true;
            appendError(code,
                    fieldName + " is mandatory",
                    errorCode,
                    errorDesc);
        } else if (value != null && value.length() > maxLength) {
            failed = true;
            appendError(code,
                    fieldName + " length should be <= " + maxLength,
                    errorCode,
                    errorDesc);
        }
        return failed;
    }

    private void appendError(String code,
                             String desc,
                             StringBuilder errorCode,
                             StringBuilder errorDesc) {

        errorCode.append(code).append(",");
        errorDesc.append(desc).append(". ");
    }

    private String trimComma(String value) {
        return value.endsWith(",")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
