package com.shoesshop.com.feign.error;

import feign.RetryableException;
import feign.Retryer;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign retryer with exponential backoff: waits {@code baseDelay × multiplier^n} before retry n
 * (200ms, 400ms, 800ms, 1.6s, 3.2s with the defaults), and gives up after {@code maxRetries}
 * retries by rethrowing the last error. Feign clones it per request, so the counter is per call.
 */
@Slf4j
public class ExponentialRetryer implements Retryer {

  private final Duration baseDelay;
  private final int multiplier;
  private final int maxRetries;
  private int retries;

  public ExponentialRetryer(Duration baseDelay, int multiplier, int maxRetries) {
    this.baseDelay = baseDelay;
    this.multiplier = multiplier;
    this.maxRetries = maxRetries;
  }

  @Override
  public void continueOrPropagate(RetryableException e) {
    if (retries >= maxRetries) {
      log.warn("Payment provider call failed after {} retries: {}", retries, e.getMessage());
      throw e;
    }
    long delay = baseDelay.toMillis() * (long) Math.pow(multiplier, retries);
    retries++;
    log.info("Payment provider call failed ({}), retry {}/{} in {} ms",
        e.getMessage(), retries, maxRetries, delay);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw e;
    }
  }

  @Override
  public Retryer clone() {
    return new ExponentialRetryer(baseDelay, multiplier, maxRetries);
  }
}
