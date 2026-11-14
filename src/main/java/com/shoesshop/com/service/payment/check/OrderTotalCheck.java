package com.shoesshop.com.service.payment.check;

import com.shoesshop.com.exception.PaymentConflictException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** There must be an amount to charge (lines without a price leave the total empty). */
@Order(4)
@Component
public class OrderTotalCheck implements PaymentCheck {

  @Override
  public void verify(PaymentContext context) {
    if (context.order().getTotal() == null) {
      throw new PaymentConflictException(
          "The order [" + context.orderId() + "] has no total to pay");
    }
  }
}
