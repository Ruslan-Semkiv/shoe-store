package com.shoesshop.com.service;

import com.shoesshop.com.config.CartProperties;
import com.shoesshop.com.dto.request.OrderLineRequestDto;
import com.shoesshop.com.dto.response.CartLineResponseDto;
import com.shoesshop.com.dto.response.CartResponseDto;
import com.shoesshop.com.entity.ItemEntity;
import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.repository.OrderJpaRepository;
import com.shoesshop.com.repository.OrderLineJpaRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The cart is an {@link OrderEntity} in {@link OrderStatus#CART} status, and its lines are
 * ordinary {@link OrderLineEntity} rows that also hold the stock (see {@link OrderLineService}).
 *
 * <p>Each operation reads the cart's lines once (locked) and builds the response from that same
 * list. Hold flow: add, open cart. Release flow: subtract, remove. Heartbeats only extend holds up
 * to {@code maxHold}.
 */
@RequiredArgsConstructor
@Service
public class CartService {

  private final OrderJpaRepository orderRepository;
  private final OrderLineJpaRepository orderLineRepository;
  private final CartProperties properties;
  private final OrderService orderService;
  private final OrderLineService orderLineService;

  /**
   * First "Buy" creates the cart order; later ones add to it. Throws if stock can't be locked.
   */
  @Transactional
  public CartResponseDto addLine(UUID orderId, OrderLineRequestDto dto) {
    Instant now = Instant.now();

    OrderEntity order = orderService.findOrCreateOrderCart(orderId, now);
    List<OrderLineEntity> lines = lockedLines(order.getId());
    orderLineService.addQuantity(order, lines, dto, now);
    return toResponse(order.getId(), lines);
  }

  /** Takes {@code dto.quantity()} units of the item out of the cart and gives them back. */
  @Transactional
  public CartResponseDto subtractLineQuantity(UUID orderId, OrderLineRequestDto dto) {
    OrderEntity order = orderRepository.getWithStatusOrElseThrow(orderId);
    order.setLastSeenAt(Instant.now());

    List<OrderLineEntity> lines = lockedLines(orderId);
    orderLineService.subtractQuantity(lines, dto.itemId(), dto.quantity());
    return toResponse(orderId, lines);
  }

  @Transactional
  public CartResponseDto removeLine(UUID orderId, UUID itemId) {
    OrderEntity order = orderRepository.getWithStatusOrElseThrow(orderId);
    order.setLastSeenAt(Instant.now());

    List<OrderLineEntity> lines = lockedLines(orderId);
    orderLineService.releaseOrderLine(lines, itemId);
    return toResponse(orderId, lines);
  }

  /**
   * Customer comes back (or opens the cart page): same cookie, same cart order. Every line gets
   * its hold renewed, and lines whose hold expired are re-tried; if stock is gone meanwhile the
   * line stays with reservedQuantity lower than quantity.
   */
  @Transactional
  public CartResponseDto getCart(UUID orderId) {
    OrderEntity order = orderId == null ? null
        : orderRepository.findLockedByIdAndStatus(orderId, OrderStatus.CART).orElse(null);
    if (order == null) {
      return CartResponseDto.empty();
    }

    Instant now = Instant.now();
    order.setLastSeenAt(now);
    List<OrderLineEntity> lines = lockedLines(orderId);
    for (OrderLineEntity line : lines) {
      orderLineService.restoreHold(line, now);
    }
    return toResponse(orderId, lines);
  }

  /**
   * Browser ping every ~2 min while the tab is open.
   */
  @Transactional
  public void heartbeat(UUID orderId) {
    OrderEntity order = orderRepository.getWithStatusOrElseThrow(orderId);
    Instant now = Instant.now();
    order.setLastSeenAt(now);
    orderLineRepository.extendHolds(orderId, now.plus(properties.holdTtl()), now);
  }

  /** The cart's lines, SELECT ... FOR UPDATE; a mutable list so services can add or drop lines. */
  private List<OrderLineEntity> lockedLines(UUID orderId) {
    return new ArrayList<>(orderLineRepository.findAllLockedByOrderId(orderId));
  }

  private static CartResponseDto toResponse(UUID orderId, List<OrderLineEntity> lines) {
    List<CartLineResponseDto> response = lines.stream()
        .map(line -> {
          ItemEntity item = line.getItem();
          int reserved = line.getReservedQuantity();
          return new CartLineResponseDto(
              line.getId(), item.getId(), item.getName(), item.getModel(), item.getColour(),
              item.getSize(), line.getQuantity(), line.getCurrency(),
              reserved, reserved > 0 ? line.getReservedUntil() : null);
        })
        .toList();
    return new CartResponseDto(orderId, response);
  }
}
