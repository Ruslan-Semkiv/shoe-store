package com.shoesshop.com.dto.response;

import com.shoesshop.com.enums.Currency;
import java.time.Instant;
import java.util.UUID;

/**
 * @param quantity         what the customer asked for
 * @param reservedQuantity how many units are actually locked for them right now
 *                         (less than quantity = the rest was sold out meanwhile)
 * @param reservedUntil    when the lock runs out unless the browser pings /heartbeat
 */
public record CartLineResponseDto(
    UUID orderLineId,
    UUID itemId,
    String name,
    String model,
    String colour,
    Short size,
    Integer quantity,
    Currency currency,
    Integer reservedQuantity,
    Instant reservedUntil
) {
}
