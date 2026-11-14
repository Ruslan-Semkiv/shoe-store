package com.shoesshop.com.mock;

import com.shoesshop.com.feign.dto.ChargeRequest;
import com.shoesshop.com.feign.dto.ChargeResponse;
import com.shoesshop.com.enums.ChargeStatus;
import com.shoesshop.com.config.PaymentProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imitates an external payment provider, so the payment flow can run without a real one.
 * Only exists with {@code shop.payment.mock.enabled=true}.
 *
 * <p>Like a real provider it remembers charges by {@code Idempotency-Key}: the same key is never
 * charged twice. Failures come from the configured rates, or from a script of next outcomes
 * ({@code POST /mock-provider/admin/script}) for deterministic tests.
 */
@Slf4j
@ConditionalOnProperty(name = "shop.payment.mock.enabled", havingValue = "true")
@RequestMapping("/mock-provider")
@RestController
public class MockPaymentProviderController {

  /** What the next charge call does. */
  public enum Outcome {
    /** charge it */
    OK,
    /** 402, card declined */
    DECLINE,
    /** 503, nothing charged */
    ERROR,
    /** hang past the caller's read timeout, nothing charged */
    TIMEOUT,
    /** charge it, but answer too late: the caller sees a timeout although money was taken */
    CHARGE_THEN_TIMEOUT
  }

  public record Script(List<Outcome> outcomes) {
  }

  public record Stats(int chargeCalls, int charges) {
  }

  private final PaymentProperties.Mock settings;
  private final Map<UUID, ChargeResponse> charges = new ConcurrentHashMap<>();
  private final Queue<Outcome> script = new ConcurrentLinkedQueue<>();
  private final AtomicInteger chargeCalls = new AtomicInteger();

  public MockPaymentProviderController(PaymentProperties properties) {
    this.settings = properties.mock();
  }

  @PostMapping("/v1/charges")
  public ResponseEntity<ChargeResponse> charge(
      @RequestHeader("Idempotency-Key") UUID key, @RequestBody ChargeRequest request)
      throws InterruptedException {
    chargeCalls.incrementAndGet();

    ChargeResponse existing = charges.get(key);
    if (existing != null) {
      log.info("[mock provider] {} already processed, returning the same charge", key);
      return answer(existing);
    }

    Outcome outcome = nextOutcome();
    log.info("[mock provider] charge {} {} {}: {}", key, request.amount(), request.currency(),
        outcome);
    switch (outcome) {
      case ERROR -> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
      }
      case TIMEOUT -> {
        Thread.sleep(settings.timeoutSleep().toMillis());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
      }
      case CHARGE_THEN_TIMEOUT -> {
        ChargeResponse charge = store(key, request, ChargeStatus.SUCCEEDED, null);
        Thread.sleep(settings.timeoutSleep().toMillis());
        return answer(charge);
      }
      case DECLINE -> {
        return answer(store(key, request, ChargeStatus.DECLINED, "insufficient_funds"));
      }
      default -> {
        return answer(store(key, request, ChargeStatus.SUCCEEDED, null));
      }
    }
  }

  @GetMapping("/v1/charges/{key}")
  public ResponseEntity<ChargeResponse> getCharge(@PathVariable UUID key) {
    ChargeResponse charge = charges.get(key);
    return charge == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(charge);
  }

  /** Test helper: the next charge calls get these outcomes, in order. */
  @PostMapping("/admin/script")
  public ResponseEntity<Void> setScript(@RequestBody Script newScript) {
    script.clear();
    script.addAll(newScript.outcomes());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/admin/stats")
  public Stats stats() {
    return new Stats(chargeCalls.get(), charges.size());
  }

  @DeleteMapping("/admin")
  public ResponseEntity<Void> reset() {
    script.clear();
    charges.clear();
    chargeCalls.set(0);
    return ResponseEntity.noContent().build();
  }

  private Outcome nextOutcome() {
    Outcome scripted = script.poll();
    if (scripted != null) {
      return scripted;
    }
    double roll = ThreadLocalRandom.current().nextDouble();
    if (roll < settings.errorRate()) {
      return Outcome.ERROR;
    }
    if (roll < settings.errorRate() + settings.timeoutRate()) {
      return Outcome.TIMEOUT;
    }
    if (roll < settings.errorRate() + settings.timeoutRate() + settings.declineRate()) {
      return Outcome.DECLINE;
    }
    return Outcome.OK;
  }

  private ChargeResponse store(UUID key, ChargeRequest request, ChargeStatus status,
                               String declineReason) {
    ChargeResponse charge = new ChargeResponse("ch_" + UUID.randomUUID().toString().substring(0, 8),
        key, status, request.amount(), request.currency(), declineReason, Instant.now());
    charges.put(key, charge);
    return charge;
  }

  private static ResponseEntity<ChargeResponse> answer(ChargeResponse charge) {
    HttpStatus status =
        charge.status() == ChargeStatus.DECLINED ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.OK;
    return ResponseEntity.status(status).body(charge);
  }
}
