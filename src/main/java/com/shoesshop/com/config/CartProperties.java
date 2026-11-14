package com.shoesshop.com.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for anonymous carts and stock holds ({@code shop.cart.*} in application.yaml).
 *
 * @param holdTtl          how long a hold lives after the last add / heartbeat
 * @param maxHold          hard cap: heartbeats cannot extend a hold past this (counted from the
 *                         last explicit cart action), so an idle open tab can't lock stock forever
 * @param cookieMaxAge     lifetime of the cart_id cookie (the cart itself, not the holds)
 * @param cookieSecure     send the cookie only over HTTPS (turn on in production)
 * @param releaseBatchSize how many expired holds one release-job run frees
 * @param abandonedAfter   carts not seen for this long (and without holds) are deleted
 */
@ConfigurationProperties(prefix = "shop.cart")
public record CartProperties(
    @DefaultValue("15m") Duration holdTtl,
    @DefaultValue("60m") Duration maxHold,
    @DefaultValue("30d") Duration cookieMaxAge,
    @DefaultValue("false") boolean cookieSecure,
    @DefaultValue("500") int releaseBatchSize,
    @DefaultValue("30d") Duration abandonedAfter
) {
}
