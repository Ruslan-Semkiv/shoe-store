package com.shoesshop.com.service;

import com.shoesshop.com.dto.response.PaymentResponseDto;
import com.shoesshop.com.dto.response.PaymentStatusResponseDto;
import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.exception.PaymentNotFoundException;
import com.shoesshop.com.feign.PaymentProviderGateway;
import com.shoesshop.com.feign.dto.ChargeResponse;
import com.shoesshop.com.repository.PaymentJpaRepository;
import com.shoesshop.com.service.payment.PaymentCommandService;
import com.shoesshop.com.service.payment.PaymentExecutor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Payments after they started (starting one is {@link PaymentCommandService}): the retry job's
 * tries and the status check. Deliberately not {@code @Transactional}: provider calls run
 * between the short DB steps of {@link PaymentTxService}.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentService {

  private final PaymentTxService paymentTxService;
  private final PaymentProviderGateway paymentProvider;
  private final PaymentJpaRepository paymentRepository;
  private final PaymentExecutor paymentExecutor;

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
      paymentExecutor.apply(payment.getId(), remote.get());
      return;
    }
    paymentExecutor.execute(payment);
  }

  /**
   * Asks the provider about the payment and brings our record up to date with its answer.
   */
  public PaymentStatusResponseDto checkStatus(UUID paymentId) {
    PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow(
        () -> new PaymentNotFoundException("There is no payment with the id - " + paymentId));

    String remoteStatus;
    try {
      Optional<ChargeResponse> remote = paymentProvider.findCharge(paymentId);
      if (remote.isPresent()) {
        remoteStatus = remote.get().status().name();
        payment = paymentExecutor.apply(paymentId, remote.get());
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
}
