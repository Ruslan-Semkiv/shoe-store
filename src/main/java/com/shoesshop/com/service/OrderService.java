package com.shoesshop.com.service;

import com.shoesshop.com.dto.request.OrderLineRequestDto;
import com.shoesshop.com.dto.request.OrderRequestDto;
import com.shoesshop.com.dto.response.OrderResponseDto;
import com.shoesshop.com.entity.ItemEntity;
import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.enums.PaymentStatus;
import com.shoesshop.com.enums.PaymentType;
import com.shoesshop.com.exception.CartCheckoutException;
import com.shoesshop.com.exception.ItemStockNumberException;
import com.shoesshop.com.exception.OrderNotFoundException;
import com.shoesshop.com.exception.PaymentConflictException;
import com.shoesshop.com.mapper.OrderMapper;
import com.shoesshop.com.repository.ItemJpaRepository;
import com.shoesshop.com.repository.OrderJpaRepository;
import com.shoesshop.com.repository.OrderLineJpaRepository;
import com.shoesshop.com.repository.PaymentJpaRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderService {

  private final OrderJpaRepository orderJpaRepository;
  private final OrderMapper mapper;
  private final OrderLineJpaRepository orderLineRepository;
  private final OrderLineService orderLineService;
  private final ItemJpaRepository itemRepository;
  private final PaymentJpaRepository paymentRepository;

  public Page<OrderResponseDto> getAll(Pageable pageable) {
    // carts are customers' private baskets, reachable only through their cart_id cookie
    return orderJpaRepository.findAllByStatusNot(OrderStatus.CART, pageable).map(mapper::toDto);
  }

  public OrderResponseDto getById(UUID id) {
    OrderEntity entity = orderJpaRepository.findByIdAndStatusNot(id, OrderStatus.CART).orElseThrow(
        () -> new OrderNotFoundException("There is no order with the provided id - " + id));
    return mapper.toDto(entity);
  }

  /**
   * Places an order directly (without a cart). Stock is taken with the same atomic
   * reserve-then-sell path as checkout, so units held in other customers' carts are never sold.
   * All or nothing: one short item rolls the whole order back.
   */
  @Transactional
  public OrderResponseDto createOrder(OrderRequestDto requestDto) {
    List<OrderLineRequestDto> orderLines = requestDto.orderLines();

    Instant now = Instant.now();
    OrderEntity order = new OrderEntity();
    order.setStatus(OrderStatus.PLACED);
    order.setPaymentType(requestDto.paymentType());
    order.setCreatedAt(now);
    order.setLastSeenAt(now);
    order = orderJpaRepository.save(order);

    List<OrderLineEntity> lines = new ArrayList<>();
    int itemCounter = 0;
    for (OrderLineRequestDto lineDto : orderLines) {
      ItemEntity item = itemRepository.getOrElseThrow(lineDto.itemId());
      int quantity = lineDto.quantity();

      if (itemRepository.tryReserve(item.getId(), quantity) == 0
          || itemRepository.commitReserved(item.getId(), quantity) == 0) {
        throw new ItemStockNumberException(
            "The item with provided id - [" + item.getId() + "], has not enough units in stock");
      }

      OrderLineEntity line = new OrderLineEntity();
      line.setOrder(order);
      line.setItem(item);
      line.setQuantity(quantity);
      line.setUnitPrice(item.getPrice());
      line.setCurrency(item.getCurrency());
      lines.add(orderLineRepository.save(line));
      itemCounter += quantity;
    }

    order.setItemCounter(itemCounter);
    order.setTotal(total(lines));
    return toResponse(order, lines);
  }

  /**
   * Turns the cart (order in CART status) into a placed order, all or nothing:
   * every line's held units are taken out of stock, holds are deleted, status becomes PLACED.
   * If a hold expired and the stock was sold meanwhile, nothing is changed and
   * {@link ItemStockNumberException} says which item is short.
   */
  @Transactional
  public OrderResponseDto checkout(UUID cartOrderId, PaymentType paymentType) {
    if (cartOrderId == null) {
      throw new OrderNotFoundException("There is no cart_id cookie");
    }
    OrderEntity order = orderJpaRepository.findLockedByIdAndStatus(cartOrderId, OrderStatus.CART)
        .orElseThrow(() -> new OrderNotFoundException(
            "There is no cart order, with the id -" + cartOrderId));

    List<OrderLineEntity> lines = orderLineRepository.findAllLockedByOrderId(cartOrderId);
    if (lines.isEmpty()) {
      throw new CartCheckoutException("The cart is empty, there is nothing to check out");
    }

    int itemCounter = 0;
    for (OrderLineEntity line : lines) {
      sellHeldUnits(line);
      itemCounter += line.getQuantity();
    }

    order.setStatus(OrderStatus.PLACED);
    order.setPaymentType(paymentType);
    order.setItemCounter(itemCounter);
    order.setTotal(total(lines));
    order.setLastSeenAt(Instant.now());

    return toResponse(order, lines);
  }

  private static OrderResponseDto toResponse(OrderEntity order, List<OrderLineEntity> lines) {
    return new OrderResponseDto(order.getId(), order.getStatus(), order.getTotal(),
        order.getPaymentType(),
        lines.stream().map(OrderLineEntity::getId).toList());
  }

  /**
   * Locks whatever the line is still missing (its hold may have expired), then sells it.
   */
  private void sellHeldUnits(OrderLineEntity line) {
    UUID itemId = line.getItem().getId();
    int quantity = line.getQuantity();

    if (!orderLineService.restoreHold(line, Instant.now())
        || itemRepository.commitReserved(itemId, quantity) == 0) {
      throw new ItemStockNumberException(
          "The item with provided id - [" + itemId + "], has not enough units in stock");
    }
    line.setUnitPrice(line.getItem().getPrice());
    // sold now: the units left items.reserved_units in commitReserved
    line.setReservedQuantity(0);
    line.setReservedUntil(null);
    line.setReservedMaxUntil(null);
  }

  /**
   * Sum of unitPrice * quantity; null while any line has no price (items have no price yet).
   */
  private static Long total(List<OrderLineEntity> lines) {
    long total = 0;
    for (OrderLineEntity line : lines) {
      if (line.getUnitPrice() == null) {
        return null;
      }
      total += line.getUnitPrice() * line.getQuantity();
    }
    return total;
  }

  /**
   * Cart order: its held units are released. Placed order: its sold units go back to stock
   * (a PAYMENT_FAILED order was already restocked when its payment gave up).
   * 409 for an order whose money is taken or may be taken: a paid order (needs a refund, not a
   * delete) or one with a payment still running or waiting for the retry job. Deleting those
   * would also delete the payment row, and a later charge would be lost.
   */
  @Transactional
  public void removeOrder(UUID id) {
    // locked: a payment can't start for this order while it is being deleted
    OrderEntity order = orderJpaRepository.findLockedById(id).orElseThrow(
        () -> new OrderNotFoundException("There is no order with the provided id - " + id));

    if (order.getStatus() == OrderStatus.PAID || paymentRepository.existsByOrderIdAndStatusIn(id,
        List.of(PaymentStatus.PROCESSING, PaymentStatus.RETRY_PENDING, PaymentStatus.SUCCEEDED))) {
      throw new PaymentConflictException("The order [" + id + "] is paid or being paid,"
          + " it can't be deleted");
    }

    for (OrderLineEntity line : orderLineRepository.findAllLockedByOrderId(id)) {
      orderLineService.releaseReserved(line);
      if (order.getStatus() == OrderStatus.PLACED
          && line.getQuantity() != null && line.getQuantity() > 0) {
        itemRepository.restock(line.getItem().getId(), line.getQuantity());
      }
    }
    orderJpaRepository.delete(order);
  }

  @Transactional
  public void cancelOrder(UUID orderId) {
    OrderEntity order = orderJpaRepository.findLockedById(orderId).orElseThrow(
        () -> new OrderNotFoundException("There is no order with the provided id - " + orderId));
    order.setStatus(OrderStatus.PAYMENT_FAILED);

    List<OrderLineEntity> orderLineEntities = orderLineRepository.findAllByOrderId(orderId);

    for (OrderLineEntity line : orderLineEntities) {
      itemRepository.restock(line.getItem().getId(), line.getQuantity());
    }
  }


  /**
   * Runs in the caller's transaction (CartService).
   */
  @Transactional
  public OrderEntity findOrCreateOrderCart(UUID orderId, Instant now) {
    OrderEntity order = orderId == null ? null
        : orderJpaRepository.findLockedByIdAndStatus(orderId, OrderStatus.CART).orElse(null);

    if (order == null) {
      order = new OrderEntity();
      order.setStatus(OrderStatus.CART);
      order.setCreatedAt(now);
      order.setLastSeenAt(now);
      return orderJpaRepository.save(order);
    }
    order.setLastSeenAt(now);
    return order;
  }

}
