package com.shoesshop.com.dto.response;

import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.enums.PaymentType;
import java.util.List;
import java.util.UUID;

/** {@code total} is in minor units (cents), like item prices and payment amounts. */
public record OrderResponseDto(
    UUID id, OrderStatus status, Long total, PaymentType paymentType, List<UUID> orderLines
) {
}
