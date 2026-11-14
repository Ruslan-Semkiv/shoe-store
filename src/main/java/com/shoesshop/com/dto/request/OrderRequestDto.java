package com.shoesshop.com.dto.request;

import com.shoesshop.com.enums.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Each line is validated too (quantity and itemId are required). */
public record OrderRequestDto(
   @NotNull PaymentType paymentType, @NotEmpty List<@Valid @NotNull OrderLineRequestDto> orderLines
) {
}
