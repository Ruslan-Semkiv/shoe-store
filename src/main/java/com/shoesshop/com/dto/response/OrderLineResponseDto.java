package com.shoesshop.com.dto.response;

import com.shoesshop.com.enums.Currency;
import java.util.UUID;

public record OrderLineResponseDto(
    UUID id,
    Integer quantity,
    Long unitPrice,
    Currency currency,
    UUID itemId,
    UUID orderId
) {
}
