package com.shoesshop.com.util;

import com.shoesshop.com.config.CartProperties;
import com.shoesshop.com.dto.response.CartResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CookieUtils {
  public static final String CART_COOKIE = "cart_id";

  private final CartProperties properties;


  /** Re-sends the cookie on every cart response, so its Max-Age slides with activity. */
  public ResponseEntity<CartResponseDto> withCartCookie(CartResponseDto body) {
    if (body.orderId() == null) {
      return ResponseEntity.ok(body);
    }
    ResponseCookie cookie = ResponseCookie.from(CART_COOKIE, body.orderId().toString())
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Lax")
        .path("/")
        .maxAge(properties.cookieMaxAge())
        .build();
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
  }

  public ResponseCookie clearCookies(){
    return ResponseCookie.from(CART_COOKIE, "")
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Lax")
        .path("/")
        .maxAge(0)
        .build();
  }
}
