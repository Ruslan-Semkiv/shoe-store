package com.shoesshop.com.feign.error;

import com.shoesshop.com.exception.ChargeNotFoundException;
import com.shoesshop.com.exception.PaymentDeclinedException;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sorts the provider's error answers into "retry" and "don't retry".
 *
 * <ul>
 *   <li>5xx, 429: the provider's technical problem → {@link feign.RetryableException}, so the
 *       {@link ExponentialRetryer} tries again. (Network timeouts and refused connections never
 *       reach here: Feign turns those IOExceptions into RetryableException itself.)</li>
 *   <li>404: no charge for that key → {@link ChargeNotFoundException}.</li>
 *   <li>Other 4xx (402 = card declined): business refusal → {@link PaymentDeclinedException},
 *       not retried.</li>
 * </ul>
 */
public class PaymentErrorDecoder implements ErrorDecoder {

  @Override
  public Exception decode(String methodKey, Response response) {
    int status = response.status();
    String body = body(response);

    if (status >= 500 || status == 429) {
      return new RetryableException(status,
          "Payment provider error " + status + " on " + methodKey + ": " + body,
          response.request().httpMethod(), (Long) null, response.request());
    }
    if (status == 404) {
      return new ChargeNotFoundException("The payment provider has no such charge: " + body);
    }
    return new PaymentDeclinedException("Payment declined (" + status + "): " + body);
  }

  private static String body(Response response) {
    if (response.body() == null) {
      return "";
    }
    try (InputStream in = response.body().asInputStream()) {
      String text = new String(in.readNBytes(400), StandardCharsets.UTF_8);
      return text.isBlank() ? "" : text;
    } catch (IOException e) {
      return "";
    }
  }
}
