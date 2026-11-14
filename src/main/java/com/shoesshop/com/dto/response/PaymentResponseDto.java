package com.shoesshop.com.dto.response;

import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.enums.Currency;
import com.shoesshop.com.enums.PaymentStatus;
import com.shoesshop.com.enums.PaymentType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param nextAttemptAt RETRY_PENDING only: when the retry job tries again
 */
public record PaymentResponseDto(
    UUID paymentId,
    UUID orderId,
    PaymentStatus status,
    Long amount,
    Currency currency,
    PaymentType paymentType,
    String externalChargeId,
    Integer jobAttempts,
    Instant nextAttemptAt,
    String lastError
) {

  public static PaymentResponseDto from(PaymentEntity payment) {
    return new PaymentResponseDto(payment.getId(), payment.getOrderId(), payment.getStatus(),
        payment.getAmount(), payment.getCurrency(), payment.getPaymentType(),
        payment.getExternalChargeId(), payment.getJobAttempts(),
        payment.getStatus() == PaymentStatus.RETRY_PENDING ? payment.getNextAttemptAt() : null,
        payment.getLastError());
  }
}
