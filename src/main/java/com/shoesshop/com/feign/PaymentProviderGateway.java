package com.shoesshop.com.feign;

import com.shoesshop.com.exception.ChargeNotFoundException;
import com.shoesshop.com.exception.PaymentDeclinedException;
import com.shoesshop.com.feign.dto.ChargeRequest;
import com.shoesshop.com.feign.dto.ChargeResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * The two Feign clients of the payment provider:
 * <ul>
 *   <li>{@link #charge}: with the exponential retries; throws the last error once they run
 *       out.</li>
 *   <li>{@link #findCharge}: a single quick call, for status checks.</li>
 * </ul>
 */
@Component
public class PaymentProviderGateway {

  private final PaymentProviderClient retryingClient;
  private final PaymentProviderClient singleCallClient;


  public PaymentProviderGateway(
      @Qualifier("paymentProviderClient") PaymentProviderClient retryingClient,
      @Qualifier("singleCallPaymentClient") PaymentProviderClient singleCallClient) {
    this.retryingClient = retryingClient;
    this.singleCallClient = singleCallClient;
  }

  /**
   * Charges with up to {@code maxRetries} retries on technical errors.
   *
   * @throws PaymentDeclinedException
   *     the provider refused it (not retried)
   * @throws feign.RetryableException
   *     every retry failed (provider error or network timeout)
   */
  public ChargeResponse charge(UUID idempotencyKey, ChargeRequest request) {
    return retryingClient.charge(idempotencyKey, request);
  }

  /**
   * The provider's charge for this key, or empty if it never charged it. One call, no retry.
   */
  public Optional<ChargeResponse> findCharge(UUID idempotencyKey) {
    try {
      return Optional.of(singleCallClient.getChargeStatus(idempotencyKey));
    } catch (ChargeNotFoundException e) {
      return Optional.empty();
    }
  }
}
