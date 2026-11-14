package com.shoesshop.com.service.payment.check;

import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.enums.Currency;
import com.shoesshop.com.enums.PaymentType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What the payment checks look at: the order (locked for the whole check-and-start transaction),
 * its lines, and the payment type sent with the request (null = the one chosen at checkout).
 */
public record PaymentContext(OrderEntity order,
                             List<OrderLineEntity> lines,
                             PaymentType requestedType) {

  public UUID orderId() {
    return order.getId();
  }

  /** The currencies of the order's lines; a payable order has exactly one. */
  public Set<Currency> currencies() {
    return lines.stream().map(OrderLineEntity::getCurrency).collect(Collectors.toSet());
  }

  /** The type sent with the payment, or the one chosen at checkout. */
  public PaymentType paymentType() {
    return requestedType != null ? requestedType : order.getPaymentType();
  }
}
