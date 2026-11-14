package com.shoesshop.com.controller;

import static com.shoesshop.com.util.CookieUtils.CART_COOKIE;

import com.shoesshop.com.dto.request.CheckoutRequestDto;
import com.shoesshop.com.dto.request.OrderLineRequestDto;
import com.shoesshop.com.dto.response.CartResponseDto;
import com.shoesshop.com.dto.response.OrderResponseDto;
import com.shoesshop.com.service.CartService;
import com.shoesshop.com.service.OrderService;
import com.shoesshop.com.util.CookieUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cart is an order in CART status. Its id travels in an HttpOnly {@code cart_id} cookie:
 * no login needed, the browser sends it automatically on every request.
 */
@RequiredArgsConstructor
@RequestMapping("/v1/cart")
@RestController
public class CartController {

  private final CookieUtils cookieUtils;
  private final CartService cartService;
  private final OrderService orderService;

  @GetMapping
  public ResponseEntity<CartResponseDto> getCart(
      @CookieValue(name = CART_COOKIE, required = false) UUID cartId) {
    return cookieUtils.withCartCookie(cartService.getCart(cartId));
  }

  /** "Buy" button. Creates the cart (and sets the cookie) on first use. 409 if out of stock. */
  @PostMapping("/lines")
  public ResponseEntity<CartResponseDto> addLine(
      @CookieValue(name = CART_COOKIE, required = false) UUID cartId,
      @RequestBody @Valid OrderLineRequestDto requestDto) {
    return cookieUtils.withCartCookie(cartService.addLine(cartId, requestDto));
  }


  /** Takes units out of the cart and gives them back to the catalog (release flow). */
  @PutMapping("/lines")
  public ResponseEntity<CartResponseDto> subtractLineQuantity(
      @CookieValue(name = CART_COOKIE, required = false) UUID cartId,
      @RequestBody @Valid OrderLineRequestDto requestDto) {
    return cookieUtils.withCartCookie(cartService.subtractLineQuantity(cartId, requestDto));
  }

  /** Removes the item's line and gives its units back. 404 if the item isn't in the cart. */
  @DeleteMapping("/lines/{itemId}")
  public ResponseEntity<CartResponseDto> removeLine(
      @CookieValue(name = CART_COOKIE, required = false) UUID cartId,
      @PathVariable @NotNull UUID itemId) {
    return cookieUtils.withCartCookie(cartService.removeLine(cartId, itemId));
  }

  /** Keeps the holds alive while the tab is open. 404 = cart is gone, stop pinging. */
  @PostMapping("/heartbeat")
  public ResponseEntity<Void> heartbeat(
      @CookieValue(name = CART_COOKIE, required = false) UUID cartId) {
    cartService.heartbeat(cartId);
    return ResponseEntity.noContent().build();
  }

  /**
   * "Pay" button: the cart order becomes PLACED and its held units are sold. The cookie is
   * cleared, so the next "Buy" starts a fresh cart. 409 if an item sold out meanwhile.
   */
  @PostMapping("/checkout")
  public ResponseEntity<OrderResponseDto> checkout(
      @CookieValue(name = CART_COOKIE, required = false) UUID cartId,
      @RequestBody @Valid CheckoutRequestDto requestDto) {

    OrderResponseDto order = orderService.checkout(cartId, requestDto.paymentType());
    ResponseCookie clearCookie = cookieUtils.clearCookies();

    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
        .body(order);
  }

}
