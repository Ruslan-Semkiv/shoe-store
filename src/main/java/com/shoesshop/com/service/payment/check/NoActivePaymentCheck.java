package com.shoesshop.com.service.payment.check;

import com.shoesshop.com.enums.PaymentStatus;
import com.shoesshop.com.exception.PaymentConflictException;
import com.shoesshop.com.repository.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One live payment per order: a new one may start only when every earlier one was declined.
 * Safe against double clicks because the order row is locked while the checks run.
 */
@RequiredArgsConstructor
@Order(2)
@Component
public class NoActivePaymentCheck implements PaymentCheck {

  private final PaymentJpaRepository paymentRepository;

  @Override
  public void verify(PaymentContext context) {
    if (paymentRepository.existsByOrderIdAndStatusNot(context.orderId(), PaymentStatus.DECLINED)) {
      throw new PaymentConflictException(
          "The order [" + context.orderId() + "] already has a payment in progress");
    }
  }
}
