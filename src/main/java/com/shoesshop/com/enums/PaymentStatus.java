package com.shoesshop.com.enums;

/**
 * PROCESSING = a provider call is running (its lease is next_attempt_at); </br>
 * RETRY_PENDING = every Feign retry failed; PaymentRetryJob tries again at next_attempt_at; </br>
 * SUCCEEDED, DECLINED, FAILED = final (FAILED = the job gave up as well).
 */
public enum PaymentStatus {

  PROCESSING, RETRY_PENDING, SUCCEEDED, DECLINED, FAILED;

  public boolean isFinal() {
    return this == SUCCEEDED || this == DECLINED || this == FAILED;
  }
}
