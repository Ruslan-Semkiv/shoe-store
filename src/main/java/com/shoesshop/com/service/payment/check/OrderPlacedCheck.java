package com.shoesshop.com.service.payment.check;

import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.exception.PaymentConflictException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Only a PLACED order is paid: not a cart, not a paid or cancelled order. */
@Order(1)
@Component
public class OrderPlacedCheck implements PaymentCheck {

  @Override
  public void verify(PaymentContext context) {
    OrderStatus status = context.order().getStatus();
    if (status != OrderStatus.PLACED) {
      throw new PaymentConflictException("The order [" + context.orderId() + "] is " + status
          + ", only PLACED orders are paid");
    }
  }
}
