package com.shoesshop.com.service;

import com.shoesshop.com.config.CartProperties;
import com.shoesshop.com.dto.request.OrderLineRequestDto;
import com.shoesshop.com.dto.response.OrderLineResponseDto;
import com.shoesshop.com.entity.ItemEntity;
import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.exception.CartCheckoutException;
import com.shoesshop.com.exception.ItemNotFoundException;
import com.shoesshop.com.exception.ItemStockNumberException;
import com.shoesshop.com.exception.OrderNotFoundException;
import com.shoesshop.com.mapper.OrderLineMapper;
import com.shoesshop.com.repository.ItemJpaRepository;
import com.shoesshop.com.repository.OrderJpaRepository;
import com.shoesshop.com.repository.OrderLineJpaRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * One line per item of an order. While the order is a cart, the line also holds stock:
 * quantity = what the customer wants, reservedQuantity = units locked for them right now
 * (counted in items.reserved_units, freed by {@code StockHoldReleaseJob} once reservedUntil
 * passes).
 */
@RequiredArgsConstructor
@Service
public class OrderLineService {

  private final OrderLineJpaRepository orderLineJpaRepository;
  private final OrderJpaRepository orderJpaRepository;
  private final ItemJpaRepository itemJpaRepository;
  private final CartProperties cartProperties;
  private final OrderLineMapper mapper;

  public Page<OrderLineResponseDto> getAllByOrderId(UUID orderId, Pageable pageable) {
    return orderLineJpaRepository.findAllByOrderId(orderId, pageable).map(mapper::toDto);
  }

  public OrderLineResponseDto getOrderLine(UUID id) {
    OrderLineEntity entity = orderLineJpaRepository.getOrElseThrow(id);
    return mapper.toDto(entity);
  }

  @Transactional
  public OrderLineResponseDto createOrderLine(UUID orderId, OrderLineRequestDto requestDto) {
    // locked, like every cart write: checkout can't place the order while a line is added
    OrderEntity orderEntity = orderJpaRepository.findLockedById(orderId).orElseThrow(
        () -> new OrderNotFoundException("There is not order, with the id -" + orderId));

    if (orderEntity.getStatus() != OrderStatus.CART) {
      throw new CartCheckoutException(
          "The order [" + orderId + "] is already placed, lines can't be added to it");
    }
    List<OrderLineEntity> lines =
        new ArrayList<>(orderLineJpaRepository.findAllLockedByOrderId(orderId));
    return mapper.toDto(addQuantity(orderEntity, lines, requestDto, Instant.now()));
  }



  /**
   * "Buy": adds to the item's line (one line per item, created on first buy and added to
   * {@code lines}) and locks the extra units. Only reserves; OrderService.checkout turns the
   * reservation into a sale (commitReserved). Throws {@link ItemStockNumberException} if the
   * units aren't in stock.
   */
  public OrderLineEntity addQuantity(OrderEntity order, List<OrderLineEntity> lines,
                                     OrderLineRequestDto requestDto, Instant now) {
    ItemEntity item = itemJpaRepository.getOrElseThrow(requestDto.itemId());
    OrderLineEntity line = findLine(lines, item.getId()).orElseGet(() -> {
      OrderLineEntity newLine = mapper.toEntity(requestDto);
      newLine.setQuantity(0);
      newLine.setReservedQuantity(0);
      newLine.setOrder(order);
      newLine.setItem(item);
      newLine.setCurrency(item.getCurrency());
      lines.add(newLine);
      return newLine;
    });

    int wanted = line.getQuantity() + requestDto.quantity();
    if (!holdUnits(line, wanted - line.getReservedQuantity(), now)) {
      throw new ItemStockNumberException(
          "The item with provided id - [" + item.getId() + "], has not enough units in stock");
    }
    line.setQuantity(wanted);
    return orderLineJpaRepository.save(line);
  }

  /**
   * Re-locks what the line is missing (its hold expired, or stock was short last time) and
   * renews the lock. Returns false, changing nothing, if those units aren't in stock.
   */
  public boolean restoreHold(OrderLineEntity line, Instant now) {
    return holdUnits(line, line.getQuantity() - line.getReservedQuantity(), now);
  }

  /** Locks {@code units} more for the line and renews its lock; false if they aren't in stock. */
  private boolean holdUnits(OrderLineEntity line, int units, Instant now) {
    if (units > 0 && itemJpaRepository.tryReserve(line.getItem().getId(), units) == 0) {
      return false;
    }
    line.setReservedQuantity(line.getReservedQuantity() + Math.max(units, 0));
    line.setReservedUntil(now.plus(cartProperties.holdTtl()));
    line.setReservedMaxUntil(now.plus(cartProperties.maxHold()));
    return true;
  }

  // ---- release flow: give units back to the catalog ----

  /**
   * Lowers the line's quantity by {@code quantity} and gives back the units it no longer needs;
   * reaching 0 removes the line.
   */
  public void subtractQuantity(List<OrderLineEntity> lines, UUID itemId, int quantity) {
    OrderLineEntity line = getLine(lines, itemId);

    int left = line.getQuantity() - quantity;
    if (left <= 0) {
      releaseReserved(line);
      orderLineJpaRepository.delete(line);
      lines.remove(line);
      return;
    }
    line.setQuantity(left);
    releaseUnits(line, line.getReservedQuantity() - left);
  }

  /** Removes the item's line and gives its locked units back; 404 if it isn't in the order. */
  public void releaseOrderLine(List<OrderLineEntity> lines, UUID itemId) {
    OrderLineEntity line = getLine(lines, itemId);
    releaseReserved(line);
    orderLineJpaRepository.delete(line);
    lines.remove(line);
  }

  /** Gives all of the line's locked units back. */
  public void releaseReserved(OrderLineEntity line) {
    releaseUnits(line, line.getReservedQuantity());
  }

  /** Gives {@code units} of the line's locked units back to the catalog. */
  private void releaseUnits(OrderLineEntity line, int units) {
    if (units <= 0) {
      return;
    }
    itemJpaRepository.release(line.getItem().getId(), units);
    line.setReservedQuantity(line.getReservedQuantity() - units);
    if (line.getReservedQuantity() == 0) {
      line.setReservedUntil(null);
      line.setReservedMaxUntil(null);
    }
  }

  private static OrderLineEntity getLine(List<OrderLineEntity> lines, UUID itemId) {
    return findLine(lines, itemId).orElseThrow(() -> new ItemNotFoundException(
        "There is no item with the id - " + itemId + " in the cart"));
  }

  private static Optional<OrderLineEntity> findLine(List<OrderLineEntity> lines, UUID itemId) {
    return lines.stream().filter(line -> line.getItem().getId().equals(itemId)).findFirst();
  }
}
