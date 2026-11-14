package com.shoesshop.com.service;

import com.shoesshop.com.config.PaymentProperties;
import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.enums.Currency;
import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.enums.PaymentStatus;
import com.shoesshop.com.enums.PaymentType;
import com.shoesshop.com.exception.OrderNotFoundException;
import com.shoesshop.com.exception.PaymentConflictException;
import com.shoesshop.com.exception.PaymentNotFoundException;
import com.shoesshop.com.repository.ItemJpaRepository;
import com.shoesshop.com.repository.OrderJpaRepository;
import com.shoesshop.com.repository.OrderLineJpaRepository;
import com.shoesshop.com.repository.PaymentJpaRepository;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final OrderLineJpaRepository orderLineRepository;
  private final ItemJpaRepository itemRepository;
  private final PaymentProperties properties;

  /**
   * Creates the PROCESSING payment of a placed order. {@code paymentType} null = the order's
   * (chosen at checkout). 409 if the order can't be paid now.
   */
  @Transactional
  public PaymentEntity start(UUID orderId, PaymentType paymentType, Instant now) {
    OrderEntity order = orderRepository.findLockedById(orderId).orElseThrow(
        () -> new OrderNotFoundException("There is no order with the provided id - " + orderId));

    if (order.getStatus() != OrderStatus.PLACED) {
      throw new PaymentConflictException(
          "The order [" + orderId + "] is " + order.getStatus() + ", only PLACED orders are paid");
    }
    if (paymentRepository.existsByOrderIdAndStatusNot(orderId, PaymentStatus.DECLINED)) {
      throw new PaymentConflictException(
          "The order [" + orderId + "] already has a payment in progress");
    }
    Set<Currency> currencies = orderLineRepository.findAllByOrderId(orderId).stream()
        .map(OrderLineEntity::getCurrency)
        .collect(Collectors.toSet());
    if (currencies.size() != 1) {
      throw new PaymentConflictException(
          "The order [" + orderId + "] must have lines in exactly one currency, has " + currencies);
    }
    if (order.getTotal() == null) {
      throw new PaymentConflictException("The order [" + orderId + "] has no total to pay");
    }
    PaymentType type = paymentType != null ? paymentType : order.getPaymentType();
    if (type == null) {
      throw new PaymentConflictException(
          "The order [" + orderId + "] has no payment type, send one with the payment");
    }

    PaymentEntity payment = new PaymentEntity();
    payment.setOrderId(orderId);
    payment.setAmount(order.getTotal());
    payment.setCurrency(currencies.iterator().next());
    payment.setPaymentType(type);
    payment.setStatus(PaymentStatus.PROCESSING);
    payment.setJobAttempts(0);
    payment.setNextAttemptAt(now.plus(properties.lease()));
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
    orderRepository.getOrElseThrow(payment.getOrderId()).setStatus(OrderStatus.PAID);
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
      cancelOrder(payment.getOrderId());
      return payment;
    }

    Duration delay = properties.jobBaseDelay().multipliedBy(
        (long) Math.pow(properties.retryMultiplier(), payment.getJobAttempts()));
    payment.setStatus(PaymentStatus.RETRY_PENDING);
    payment.setNextAttemptAt(now.plus(delay));
    return payment;
  }

  private void cancelOrder(UUID orderId) {
    OrderEntity order = orderRepository.findLockedById(orderId).orElseThrow(
        () -> new OrderNotFoundException("There is no order with the provided id - " + orderId));
    order.setStatus(OrderStatus.PAYMENT_FAILED);
    for (OrderLineEntity line : orderLineRepository.findAllByOrderId(orderId)) {
      itemRepository.restock(line.getItem().getId(), line.getQuantity());
    }
  }

  private PaymentEntity lock(UUID paymentId) {
    return paymentRepository.findLockedById(paymentId).orElseThrow(
        () -> new PaymentNotFoundException("There is no payment with the id - " + paymentId));
  }

  private static String truncate(String text) {
    return text == null || text.length() <= 500 ? text : text.substring(0, 500);
  }
}
