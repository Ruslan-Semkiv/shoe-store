package com.shoesshop.com.controller;

import com.shoesshop.com.dto.request.PaymentRequestDto;
import com.shoesshop.com.dto.response.PaymentResponseDto;
import com.shoesshop.com.dto.response.PaymentStatusResponseDto;
import com.shoesshop.com.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/v1")
@RestController
public class PaymentController {

  private final PaymentService paymentService;

  /**
   * Pays a placed order. 201 = paid; 202 = the provider kept failing, the payment is saved and
   * retried in the background (see nextAttemptAt); 402 = declined; 409 = can't be paid now.
   * The body is optional: without a paymentType the one chosen at checkout is used.
   */
  @PostMapping("/orders/{orderId}/payments")
  public ResponseEntity<PaymentResponseDto> pay(
      @PathVariable @NotNull UUID orderId,
      @RequestBody @Valid PaymentRequestDto requestDto) {

    PaymentResponseDto payment = paymentService.pay(orderId,requestDto.paymentType());

    HttpStatus status = switch (payment.status()) {
      case SUCCEEDED -> HttpStatus.CREATED;
      case DECLINED -> HttpStatus.PAYMENT_REQUIRED;
      default -> HttpStatus.ACCEPTED;
    };

    return ResponseEntity.status(status).body(payment);
  }

  /** Checks the payment on the remote provider and syncs our record with it. */
  @GetMapping("/payments/{paymentId}/status")
  public PaymentStatusResponseDto status(@PathVariable @NotNull UUID paymentId) {
    return paymentService.checkStatus(paymentId);
  }
}
