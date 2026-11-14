package com.shoesshop.com.service.payment;

import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.enums.ChargeStatus;
import com.shoesshop.com.exception.PaymentDeclinedException;
import com.shoesshop.com.feign.PaymentProviderGateway;
import com.shoesshop.com.feign.dto.ChargeRequest;
import com.shoesshop.com.feign.dto.ChargeResponse;
import com.shoesshop.com.service.PaymentTxService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Charges a PROCESSING payment on the provider and saves the outcome. Not transactional: the
 * call with all its Feign retries can take seconds, and each outcome commits on its own in
 * {@link PaymentTxService}.
 *
 * <pre>
 * Feign: call + up to 5 retries (×2 backoff) on 5xx / timeout
 *   ├─ charged          ─▶ SUCCEEDED, order PAID
 *   ├─ declined         ─▶ DECLINED (order stays PLACED)
 *   └─ retries ran out  ─▶ RETRY_PENDING ─▶ PaymentRetryJob
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentExecutor {

  private final PaymentProviderGateway paymentProvider;
  private final PaymentTxService paymentTxService;

  public PaymentEntity execute(PaymentEntity payment) {
    ChargeRequest request = new ChargeRequest(
        payment.getOrderId(), payment.getAmount(),
        payment.getCurrency(), payment.getPaymentType()
    );

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

  /** Saves what the provider says about a charge: SUCCEEDED (order PAID) or DECLINED. */
  public PaymentEntity apply(UUID paymentId, ChargeResponse charge) {
    return charge.status() == ChargeStatus.SUCCEEDED
        ? paymentTxService.markSucceeded(paymentId, charge.chargeId())
        : paymentTxService.markDeclined(paymentId, charge.chargeId(), charge.declineReason());
  }
}
