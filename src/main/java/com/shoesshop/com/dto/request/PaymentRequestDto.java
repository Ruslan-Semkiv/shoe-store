package com.shoesshop.com.dto.request;

import com.shoesshop.com.enums.PaymentType;
import jakarta.validation.constraints.NotNull;

/** Optional body: {@code paymentType} overrides the one chosen at checkout; null keeps it. */
public record PaymentRequestDto(@NotNull PaymentType paymentType) {
}
