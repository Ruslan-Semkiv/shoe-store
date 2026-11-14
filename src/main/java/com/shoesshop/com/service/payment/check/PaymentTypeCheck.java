package com.shoesshop.com.service.payment.check;

import com.shoesshop.com.exception.PaymentConflictException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The payment needs a method: sent with the request, or chosen at checkout. */
@Order(5)
@Component
public class PaymentTypeCheck implements PaymentCheck {

  @Override
  public void verify(PaymentContext context) {
    if (context.paymentType() == null) {
      throw new PaymentConflictException("The order [" + context.orderId()
          + "] has no payment type, send one with the payment");
    }
  }
}
