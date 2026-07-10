package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "NewebPay query trade response")
public class NewebPayQueryResponse {

    private String paymentNo;
    private String applicationNo;
    private String queryUrl;
    private Map<String, Object> rawResponse;

    public NewebPayQueryResponse(
            String paymentNo,
            String applicationNo,
            String queryUrl,
            Map<String, Object> rawResponse) {
        this.paymentNo = paymentNo;
        this.applicationNo = applicationNo;
        this.queryUrl = queryUrl;
        this.rawResponse = rawResponse;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public String getApplicationNo() {
        return applicationNo;
    }

    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }

    public String getQueryUrl() {
        return queryUrl;
    }

    public void setQueryUrl(String queryUrl) {
        this.queryUrl = queryUrl;
    }

    public Map<String, Object> getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(Map<String, Object> rawResponse) {
        this.rawResponse = rawResponse;
    }
}
