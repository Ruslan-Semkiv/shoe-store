package com.shoesshop.com.service;

import com.shoesshop.com.config.PaymentProperties;
import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.enums.PaymentStatus;
import com.shoesshop.com.enums.PaymentType;
import com.shoesshop.com.exception.OrderNotFoundException;
import com.shoesshop.com.exception.PaymentNotFoundException;
import com.shoesshop.com.repository.ItemJpaRepository;
import com.shoesshop.com.repository.OrderJpaRepository;
import com.shoesshop.com.repository.OrderLineJpaRepository;
import com.shoesshop.com.repository.PaymentJpaRepository;
import com.shoesshop.com.service.payment.check.PaymentContext;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.Order;
import org.springframework.stereotype.Service;

/**
 * The database steps of a payment, each one a short transaction of its own. They live in their
 * own bean so {@link PaymentService} calls them through the Spring proxy: the HTTP calls to the
 * provider then happen between transactions, never holding a DB connection or row lock.
 *
 * <p>Every "mark" method locks the payment row and only moves it out of PROCESSING /
 * RETRY_PENDING, so a late answer can never overwrite a final status.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentTxService {

  private final PaymentJpaRepository paymentRepository;
  private final OrderJpaRepository orderRepository;
  private final OrderService orderService;
  private final PaymentProperties properties;

  /**
   * Saves the PROCESSING payment of an order that passed every
   * {@link com.shoesshop.com.service.payment.check.PaymentCheck}. Joins the transaction of
   * {@link com.shoesshop.com.service.payment.PaymentCommandService}, which holds the order lock.
   */
  @Transactional
  public PaymentEntity start(PaymentContext context) {
    OrderEntity order = context.order();
    PaymentType type = context.paymentType();
    Instant now = Instant.now();

    PaymentEntity payment = new PaymentEntity();
    payment.setOrderId(order.getId());
    payment.setAmount(order.getTotal());
    payment.setCurrency(context.currencies().iterator().next());
    payment.setPaymentType(type);
    payment.setStatus(PaymentStatus.PROCESSING);
    payment.setJobAttempts(0);
    payment.setNextAttemptAt(now.plus(properties.lease())); // lease: the call below must finish by then
    payment.setCreatedAt(now);
    payment.setUpdatedAt(now);
    order.setPaymentType(type);
    return paymentRepository.save(payment);
  }

  /**
   * Claims the next due payment for the job (PROCESSING, lease starts now, job attempt counted).
   * One at a time, right before it is processed: a batch claimed up front could see its last
   * leases run out while the first payments are still being retried, and another app instance
   * would take them over.
   */
  @Transactional
  public Optional<PaymentEntity> claimNext(Instant now) {
    Optional<PaymentEntity> next = paymentRepository.findDueLocked(now, 1).stream().findFirst();
    next.ifPresent(payment -> {
      payment.setStatus(PaymentStatus.PROCESSING);
      payment.setJobAttempts(payment.getJobAttempts() + 1);
      payment.setNextAttemptAt(now.plus(properties.lease()));
      payment.setUpdatedAt(now);
    });
    return next;
  }

  /** The provider charged it: payment SUCCEEDED, order PAID. */
  @Transactional
  public PaymentEntity markSucceeded(UUID paymentId, String chargeId) {
    PaymentEntity payment = lock(paymentId);

    if (payment.getStatus() == PaymentStatus.FAILED) {
      log.error("Payment {} was charged ({}) after it had been given up and order {} cancelled:"
          + " it must be refunded", paymentId, chargeId, payment.getOrderId());
      payment.setExternalChargeId(chargeId);
      return payment;
    }

    if (payment.getStatus().isFinal()) {
      return payment;
    }

    payment.setStatus(PaymentStatus.SUCCEEDED);
    payment.setExternalChargeId(chargeId);
    payment.setNextAttemptAt(null);
    payment.setLastError(null);
    payment.setUpdatedAt(Instant.now());

    OrderEntity order = orderRepository.getOrElseThrow(payment.getOrderId());
    order.setStatus(OrderStatus.PAID);
    orderRepository.save(order);

    return payment;
  }

  /** The provider refused it. The order stays PLACED, so the customer can pay another way. */
  @Transactional
  public PaymentEntity markDeclined(UUID paymentId, String chargeId, String reason) {
    PaymentEntity payment = lock(paymentId);
    if (payment.getStatus().isFinal()) {
      return payment;
    }
    payment.setStatus(PaymentStatus.DECLINED);
    payment.setExternalChargeId(chargeId);
    payment.setNextAttemptAt(null);
    payment.setLastError(truncate(reason));
    payment.setUpdatedAt(Instant.now());
    return payment;
  }

  /**
   * Every Feign retry failed. Schedules the next job try ({@code jobBaseDelay × multiplier^n}),
   * or, after {@code maxJobAttempts} job tries, gives up: payment FAILED, order PAYMENT_FAILED,
   * its stock back to the catalog.
   */
  @Transactional
  public PaymentEntity markRetry(UUID paymentId, String error, Instant now) {
    PaymentEntity payment = lock(paymentId);

    if (payment.getStatus() != PaymentStatus.PROCESSING) {
      return payment;
    }
    payment.setLastError(truncate(error));
    payment.setUpdatedAt(now);

    if (payment.getJobAttempts() >= properties.maxJobAttempts()) {
      log.warn("Payment {} failed after {} job attempts, cancelling order {}",
          paymentId, payment.getJobAttempts(), payment.getOrderId());

      payment.setStatus(PaymentStatus.FAILED);
      payment.setNextAttemptAt(null);
      orderService.cancelOrder(payment.getOrderId());
      return payment;
    }

    Duration delay = properties.jobBaseDelay().multipliedBy(
        (long) Math.pow(properties.retryMultiplier(), payment.getJobAttempts())
    );
    payment.setStatus(PaymentStatus.RETRY_PENDING);
    payment.setNextAttemptAt(now.plus(delay));
    return payment;
  }


  private PaymentEntity lock(UUID paymentId) {
    return paymentRepository.findLockedById(paymentId).orElseThrow(
        () -> new PaymentNotFoundException("There is no payment with the id - " + paymentId));
  }

  private static String truncate(String text) {
    return text == null || text.length() <= 500 ? text : text.substring(0, 500);
  }
}
