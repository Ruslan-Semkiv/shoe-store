package com.shoesshop.com.service.payment.check;

import com.shoesshop.com.exception.PaymentConflictException;

/**
 * One rule an order must pass before a payment is started (strategy). {@code PaymentCommandService}
 * runs every implementation in {@link org.springframework.core.annotation.Order} sequence; the
 * first failing one stops the payment.
 */
public interface PaymentCheck {

  /** Throws {@link PaymentConflictException} (409) when the order can't be paid now. */
  void verify(PaymentContext context);
}
