package com.example.demo.Controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.NewebPayService;
import com.example.demo.dto.request.VendorPaymentRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NewebPayPaymentResponse;
import com.example.demo.dto.response.NewebPayQueryResponse;
import com.example.demo.dto.response.PaymentStatusResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "付款 API", description = "提供攤主建立藍新金流付款、查詢付款狀態、接收藍新付款通知與付款完成導回功能。")
public class PaymentController {

    private final NewebPayService newebPayService;
    private final String frontendUrl;

    public PaymentController(
            NewebPayService newebPayService,
            @Value("${frontend.url:http://localhost:4200}") String frontendUrl) {
        this.newebPayService = newebPayService;
        this.frontendUrl = frontendUrl;
    }

    @Operation(
            summary = "建立藍新金流付款資料",
            description = "攤主依申請編號建立藍新 MPG 付款資料，回傳 gateway、MerchantID、TradeInfo、TradeSha 與 Version，供前端送往藍新付款頁。")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/vendor/payments/newebpay")
    public ApiResponse<NewebPayPaymentResponse> createNewebPayPayment(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody VendorPaymentRequest request) {
        return newebPayService.createPayment(authorizationHeader, request);
    }

    @Operation(
            summary = "取得本地端付款狀態",
            description = "依申請編號取得本地報名付款狀態、付款單狀態、付款金額、藍新交易編號與付款時間。")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/vendor/payments/{applicationNo}/status")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable String applicationNo) {
        return newebPayService.getPaymentStatus(authorizationHeader, applicationNo);
    }

    @Operation(
            summary = "查詢藍新交易狀態",
            description = "依申請編號向藍新金流查詢交易結果，並在付款成功時同步更新本地付款狀態。")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/vendor/payments/{applicationNo}/newebpay-query")
    public ApiResponse<NewebPayQueryResponse> queryNewebPayTrade(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable String applicationNo) {
        return newebPayService.queryNewebPayTrade(authorizationHeader, applicationNo);
    }

    @Operation(
            summary = "接收藍新付款通知",
            description = "接收藍新 NotifyURL 背景通知，驗證 TradeSha、解密 TradeInfo，並依交易結果更新本地付款狀態。")
    @PostMapping("/api/newebpay/notify")
    public String receiveNotify(@RequestParam Map<String, String> payload) {
        try {
            return newebPayService.handleNotify(payload);
        } catch (RuntimeException exception) {
            return "0|" + exception.getMessage();
        }
    }

    @Operation(
            summary = "接收藍新付款導回",
            description = "接收藍新付款完成後的導回資料，驗證後重新導向前端付款結果頁。")
    @PostMapping("/api/newebpay/return")
    public ResponseEntity<Void> receiveReturn(@RequestParam Map<String, String> payload) {
        return redirectToPaymentReturn(payload);
    }

    @Operation(
            summary = "開啟藍新付款導回",
            description = "提供瀏覽器開啟藍新付款完成導回網址，會重新導向前端付款結果頁。")
    @GetMapping("/api/newebpay/return")
    public ResponseEntity<Void> openReturn(@RequestParam Map<String, String> payload) {
        return redirectToPaymentReturn(payload);
    }

    private ResponseEntity<Void> redirectToPaymentReturn(Map<String, String> payload) {
        return ResponseEntity
                .status(302)
                .header(HttpHeaders.LOCATION, newebPayService.buildReturnUrl(payload, frontendUrl))
                .build();
    }
}
