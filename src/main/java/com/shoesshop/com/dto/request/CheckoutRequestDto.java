package com.shoesshop.com.dto.request;

import com.shoesshop.com.enums.PaymentType;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequestDto(@NotNull PaymentType paymentType) {
}
