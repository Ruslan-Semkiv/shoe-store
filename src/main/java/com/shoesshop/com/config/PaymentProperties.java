package com.shoesshop.com.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Payment provider and retry settings ({@code shop.payment.*} in application.yaml).
 *
 * @param baseUrl         the provider's address; required (the "local" profile sets the mock's)
 * @param connectTimeout  Feign connect timeout
 * @param readTimeout     Feign read timeout; a slower answer counts as a network failure
 * @param retryBaseDelay  wait before the first Feign retry
 * @param retryMultiplier each next Feign retry waits this many times longer
 * @param maxRetries      Feign retries per payment try; after them the payment is saved as
 *                        RETRY_PENDING for {@code PaymentRetryJob}
 * @param jobBaseDelay    the job's first retry comes this long after the failed try; every next
 *                        one waits {@code retryMultiplier} times longer
 * @param maxJobAttempts  job tries before the payment is FAILED and the order cancelled
 * @param jobBatchSize    payments one job run picks up
 * @param lease           how long a running provider call owns its payment row; if the app dies
 *                        mid-call, the job takes the payment over after this
 * @param mock            the built-in fake provider
 */
@ConfigurationProperties(prefix = "shop.payment")
public record PaymentProperties(
    String baseUrl,
    @DefaultValue("1s") Duration connectTimeout,
    @DefaultValue("2s") Duration readTimeout,
    @DefaultValue("200ms") Duration retryBaseDelay,
    @DefaultValue("2") int retryMultiplier,
    @DefaultValue("5") int maxRetries,
    @DefaultValue("1m") Duration jobBaseDelay,
    @DefaultValue("5") int maxJobAttempts,
    @DefaultValue("20") int jobBatchSize,
    @DefaultValue("2m") Duration lease,
    @DefaultValue Mock mock
) {

  /**
   * @param enabled     expose the fake provider under /mock-provider
   * @param errorRate   share of charge calls answered with 503 (0..1)
   * @param timeoutRate share of charge calls that hang longer than our read timeout (0..1)
   * @param declineRate share of charge calls declined with 402 (0..1)
   * @param timeoutSleep how long a "hanging" call hangs
   */
  public record Mock(
      @DefaultValue("false") boolean enabled,
      @DefaultValue("0") double errorRate,
      @DefaultValue("0") double timeoutRate,
      @DefaultValue("0") double declineRate,
      @DefaultValue("3s") Duration timeoutSleep
  ) {
  }
}
