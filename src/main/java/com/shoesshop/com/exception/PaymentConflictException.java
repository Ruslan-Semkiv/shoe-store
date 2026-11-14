package com.shoesshop.com.exception;

/** The order can't be paid now: not placed, already paid / being paid, no total, mixed currencies. */
public class PaymentConflictException extends RuntimeException {
  public PaymentConflictException(String message) {
    super(message);
  }
}
