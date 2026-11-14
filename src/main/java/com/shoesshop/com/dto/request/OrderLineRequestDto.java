package com.shoesshop.com.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** The line's currency comes from the item, not from the request. */
public record OrderLineRequestDto(
    @NotNull @Positive Integer quantity,
    @NotNull UUID itemId
) {
}
