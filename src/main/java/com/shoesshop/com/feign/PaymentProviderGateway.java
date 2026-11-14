package com.shoesshop.com.feign;

import com.shoesshop.com.feign.error.ExponentialRetryer;
import com.shoesshop.com.feign.error.PaymentErrorDecoder;
import com.shoesshop.com.exception.ChargeNotFoundException;
import com.shoesshop.com.exception.PaymentDeclinedException;
import com.shoesshop.com.config.PaymentProperties;
import com.shoesshop.com.feign.dto.ChargeRequest;
import com.shoesshop.com.feign.dto.ChargeResponse;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import feign.jackson3.Jackson3Decoder;
import feign.jackson3.Jackson3Encoder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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

  public PaymentProviderGateway(PaymentProperties properties, JsonMapper jsonMapper) {
    if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
      throw new IllegalStateException("Payment provider url , should be set up.");
    }
    Retryer retryer = new ExponentialRetryer(
        properties.retryBaseDelay(), properties.retryMultiplier(), properties.maxRetries());

    this.retryingClient = build(properties, jsonMapper, retryer);
    this.singleCallClient = build(properties, jsonMapper, Retryer.NEVER_RETRY);
  }

  private static PaymentProviderClient build(PaymentProperties properties, JsonMapper jsonMapper,
                                             Retryer retryer) {
    return Feign.builder()
        .encoder(new Jackson3Encoder(jsonMapper))
        .decoder(new Jackson3Decoder(jsonMapper))
        .errorDecoder(new PaymentErrorDecoder())
        .options(new Request.Options(
            properties.connectTimeout(), properties.readTimeout(), true))
        .retryer(retryer)
        .target(PaymentProviderClient.class, properties.baseUrl());
  }

  /**
   * Charges with up to {@code maxRetries} retries on technical errors.
   *
   * @throws PaymentDeclinedException  the provider refused it (not retried)
   * @throws feign.RetryableException  every retry failed (provider error or network timeout)
   */
  public ChargeResponse charge(UUID idempotencyKey, ChargeRequest request) {
    return retryingClient.charge(idempotencyKey, request);
  }

  /** The provider's charge for this key, or empty if it never charged it. One call, no retry. */
  public Optional<ChargeResponse> findCharge(UUID idempotencyKey) {
    try {
      return Optional.of(singleCallClient.getChargeStatus(idempotencyKey));
    } catch (ChargeNotFoundException e) {
      return Optional.empty();
    }
  }
}
