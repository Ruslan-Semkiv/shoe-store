package com.shoesshop.com.dto.response;

import java.time.Instant;

/**
 * @param payment      our record, after syncing it with the provider's answer
 * @param remoteStatus what the provider says: SUCCEEDED, DECLINED, NOT_FOUND (never charged) or
 *                     UNAVAILABLE (the provider didn't answer)
 */
public record PaymentStatusResponseDto(
    PaymentResponseDto payment,
    String remoteStatus,
    Instant checkedAt
) {
}
