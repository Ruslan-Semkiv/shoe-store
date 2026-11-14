package com.shoesshop.com.service.payment.check;

import com.shoesshop.com.enums.Currency;
import com.shoesshop.com.exception.PaymentConflictException;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The provider charges one amount in one currency, so all lines must share it. */
@Order(3)
@Component
public class SingleCurrencyCheck implements PaymentCheck {

  @Override
  public void verify(PaymentContext context) {
    Set<Currency> currencies = context.currencies();
    if (currencies.size() != 1) {
      throw new PaymentConflictException("The order [" + context.orderId()
          + "] must have lines in exactly one currency, has " + currencies);
    }
  }
}
