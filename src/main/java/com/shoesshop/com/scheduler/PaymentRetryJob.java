package com.shoesshop.com.scheduler;

import com.shoesshop.com.config.PaymentProperties;
import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.service.PaymentService;
import com.shoesshop.com.service.PaymentTxService;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Processes the payments whose Feign retries all failed (RETRY_PENDING) once their
 * next_attempt_at comes, plus PROCESSING ones whose call lease ran out (the app died mid-call).
 * Each try waits twice as long as the one before; after {@code max-job-attempts} the payment is
 * FAILED and its order cancelled. Safe on several instances (FOR UPDATE SKIP LOCKED).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentRetryJob {

  private final PaymentTxService paymentTxService;
  private final PaymentService paymentService;
  private final PaymentProperties properties;

  /** Up to {@code job-batch-size} payments per run, each claimed just before it is processed. */
  @Scheduled(fixedDelayString = "${shop.payment.retry-interval:PT30S}")
  public void retryFailedPayments() {
    int retried = 0;
    while (retried < properties.jobBatchSize()) {
      Optional<PaymentEntity> next = paymentTxService.claimNext(Instant.now());
      if (next.isEmpty()) {
        break;
      }
      retried++;
      try {
        paymentService.retry(next.get());
      } catch (RuntimeException e) {
        // the lease runs out and a later run picks it up again
        log.error("Retrying payment {} failed", next.get().getId(), e);
      }
    }
    if (retried > 0) {
      log.info("Retried {} payment(s)", retried);
    }
  }
}
