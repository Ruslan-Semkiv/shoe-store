package com.shoesshop.com.dto.response;

import java.util.List;
import java.util.UUID;

/** The cart view of an order in CART status. */
public record CartResponseDto(UUID orderId, List<CartLineResponseDto> lines) {

  public static CartResponseDto empty() {
    return new CartResponseDto(null, List.of());
  }
}
