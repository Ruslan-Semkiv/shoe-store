package com.shoesshop.com.exception;

/** The provider has no charge for this idempotency key (404): it was never charged. */
public class ChargeNotFoundException extends RuntimeException {
  public ChargeNotFoundException(String message) {
    super(message);
  }
}
