package com.shoesshop.com.exception;

/** Business refusal (card declined, 4xx): retrying won't help, so it is never retried. */
public class PaymentDeclinedException extends RuntimeException {
  public PaymentDeclinedException(String message) {
    super(message);
  }
}
