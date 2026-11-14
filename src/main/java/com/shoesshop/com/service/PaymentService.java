package com.shoesshop.com.service;

import com.shoesshop.com.feign.PaymentProviderGateway;
import com.shoesshop.com.feign.dto.ChargeRequest;
import com.shoesshop.com.feign.dto.ChargeResponse;
import com.shoesshop.com.enums.ChargeStatus;
import com.shoesshop.com.exception.PaymentDeclinedException;
import com.shoesshop.com.dto.response.PaymentResponseDto;
import com.shoesshop.com.dto.response.PaymentStatusResponseDto;
import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.enums.PaymentType;
import com.shoesshop.com.exception.PaymentNotFoundException;
import com.shoesshop.com.repository.PaymentJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The payment flow. Deliberately not {@code @Transactional}: a provider call with all its
 * retries can take seconds, so each DB step commits on its own in {@link PaymentTxService} and
 * the calls run in between.
 *
 * <pre>
 * pay ─▶ PROCESSING ─▶ Feign: call + up to 5 retries (×2 backoff) on 5xx / timeout
 *                       ├─ charged  ─▶ SUCCEEDED, order PAID
 *                       ├─ declined ─▶ DECLINED (order stays PLACED)
 *                       └─ retries ran out ─▶ RETRY_PENDING ─▶ PaymentRetryJob
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentService {

  private final PaymentTxService paymentTxService;
  private final PaymentProviderGateway paymentProvider;
  private final PaymentJpaRepository paymentRepository;

  /** "Pay" button of a placed order. */
  public PaymentResponseDto pay(UUID orderId, PaymentType paymentType) {
    PaymentEntity payment = paymentTxService.start(orderId, paymentType, Instant.now());
    return PaymentResponseDto.from(charge(payment));
  }

  /**
   * PaymentRetryJob's try. Asks the provider first: an earlier call that timed out on our side
   * may have been charged after all, and must not be charged again.
   */
  public void retry(PaymentEntity payment) {
    Optional<ChargeResponse> remote;
    try {
      remote = paymentProvider.findCharge(payment.getId());
    } catch (RuntimeException e) {
      paymentTxService.markRetry(payment.getId(),
          "Status check failed: " + e.getMessage(), Instant.now());
      return;
    }
    if (remote.isPresent()) {
      log.info("Payment {} was already charged by the provider ({})", payment.getId(),
          remote.get().status());
      apply(payment.getId(), remote.get());
      return;
    }
    charge(payment);
  }

  /** Asks the provider about the payment and brings our record up to date with its answer. */
  public PaymentStatusResponseDto checkStatus(UUID paymentId) {
    PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow(
        () -> new PaymentNotFoundException("There is no payment with the id - " + paymentId));

    String remoteStatus;
    try {
      Optional<ChargeResponse> remote = paymentProvider.findCharge(paymentId);
      if (remote.isPresent()) {
        remoteStatus = remote.get().status().name();
        payment = apply(paymentId, remote.get());
      } else {
        remoteStatus = "NOT_FOUND";
      }
    } catch (RuntimeException e) {
      log.warn("Payment provider status check of {} failed: {}", paymentId, e.getMessage());
      remoteStatus = "UNAVAILABLE";
    }
    return new PaymentStatusResponseDto(
        PaymentResponseDto.from(payment), remoteStatus, Instant.now());
  }

  private PaymentEntity charge(PaymentEntity payment) {
    ChargeRequest request = new ChargeRequest(payment.getOrderId(), payment.getAmount(),
        payment.getCurrency(), payment.getPaymentType());
    ChargeResponse charge;
    try {
      charge = paymentProvider.charge(payment.getId(), request);
    } catch (PaymentDeclinedException e) {
      return paymentTxService.markDeclined(payment.getId(), null, e.getMessage());
    } catch (RuntimeException e) {
      // every Feign retry failed (5xx / timeout), or another technical failure
      log.warn("Payment {} failed, saved for the retry job: {}", payment.getId(), e.getMessage());
      return paymentTxService.markRetry(payment.getId(), e.getMessage(), Instant.now());
    }
    return apply(payment.getId(), charge);
  }

  private PaymentEntity apply(UUID paymentId, ChargeResponse charge) {
    return charge.status() == ChargeStatus.SUCCEEDED
        ? paymentTxService.markSucceeded(paymentId, charge.chargeId())
        : paymentTxService.markDeclined(paymentId, charge.chargeId(), charge.declineReason());
  }
}
