package com.shoesshop.com.feign.dto;

import com.shoesshop.com.enums.Currency;
import com.shoesshop.com.enums.PaymentType;
import java.util.UUID;

/** @param amount in minor units (cents) of {@code currency} */
public record ChargeRequest(UUID orderId, Long amount, Currency currency, PaymentType paymentType) {
}
