package com.shoesshop.com.feign;

import com.shoesshop.com.feign.dto.ChargeRequest;
import com.shoesshop.com.feign.dto.ChargeResponse;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import java.util.UUID;

/**
 * Feign client of the external payment provider. Built in {@link com.shoesshop.com.feign.PaymentProviderGateway}.
 *
 * <p>Every charge carries {@code Idempotency-Key = our payment id}: a call that timed out on our
 * side may still have been charged, so a retry with the same key returns that charge instead of
 * charging the customer twice.
 */
@Headers({"Content-Type: application/json", "Accept: application/json"})
public interface PaymentProviderClient {

  @RequestLine("POST /v1/charges")
  @Headers("Idempotency-Key: {key}")
  ChargeResponse charge(@Param("key") UUID idempotencyKey, ChargeRequest request);

  /** 404 ({@link com.shoesshop.com.exception.ChargeNotFoundException}) if the provider never charged this key. */
  @RequestLine("GET /v1/charges/{key}")
  ChargeResponse getChargeStatus(@Param("key") UUID idempotencyKey);
}
