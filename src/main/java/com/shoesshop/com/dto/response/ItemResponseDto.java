package com.shoesshop.com.dto.response;

import com.shoesshop.com.enums.Currency;
import java.util.UUID;

public record ItemResponseDto(
    UUID id,
    String name,
    String colour,
    String model,
    Long unitsNumber,
    Short size,
    Long price,
    Currency currency
) {
}
