package com.shoesshop.com.feign.dto;

import com.shoesshop.com.enums.ChargeStatus;
import com.shoesshop.com.enums.Currency;
import java.time.Instant;
import java.util.UUID;

/** What the provider knows about a charge (also the body of its 402 "declined" answer). */
public record ChargeResponse(
    String chargeId,
    UUID idempotencyKey,
    ChargeStatus status,
    Long amount,
    Currency currency,
    String declineReason,
    Instant createdAt
) {
}
