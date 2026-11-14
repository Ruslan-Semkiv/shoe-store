package com.shoesshop.com.dto.request;

import com.shoesshop.com.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ItemRequestDto(
    @NotBlank String name,
    @NotBlank String colour,
    @NotBlank String model,
    @NotNull @Positive Long unitsNumber,
    @NotNull @PositiveOrZero Short size,
    @NotNull @PositiveOrZero Long price,
    @NotNull Currency currency) {
}
