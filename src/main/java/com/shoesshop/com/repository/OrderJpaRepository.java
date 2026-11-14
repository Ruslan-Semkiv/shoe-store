package com.shoesshop.com.repository;

import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.exception.OrderNotFoundException;
import com.shoesshop.com.repository.common.ValidatorJpaRepository;
import com.shoesshop.com.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderJpaRepository extends ValidatorJpaRepository<OrderEntity, UUID> {

  default OrderEntity getOrElseThrow(UUID id) {
    return getOrElseThrow(id,"There is not order, with the id -");
  }

  /**
   * The cart order, locked (SELECT ... FOR UPDATE). Every cart write takes this lock first, like
   * checkout does, so a change can't slip in while the cart is being checked out.
   */
  default OrderEntity getWithStatusOrElseThrow(UUID id) {
    if (id == null) {
      throw new OrderNotFoundException("There is no cart_id cookie");
    }
    return findLockedByIdAndStatus(id, OrderStatus.CART).orElseThrow(
        () -> new OrderNotFoundException("There is no cart order, with the id -" + id));
  }

  /** Placed, paid and failed orders; carts are left out. */
  Page<OrderEntity> findAllByStatusNot(OrderStatus status, Pageable pageable);

  Optional<OrderEntity> findByIdAndStatusNot(UUID id, OrderStatus status);

  /** The order behind the cart_id cookie, only while it is still a cart. */
  Optional<OrderEntity> findByIdAndStatus(UUID id, OrderStatus status);

  /** SELECT ... FOR UPDATE: two "Pay" clicks on one order can't start two payments. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<OrderEntity> findLockedById(UUID id);

  /** SELECT ... FOR UPDATE: a double-clicked "Pay" can't check the same cart out twice. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<OrderEntity> findLockedByIdAndStatus(UUID id, OrderStatus status);

  /** Lines of carts abandoned long ago (holds must already be released by the job). */
  @Modifying
  @Query("""
      DELETE FROM OrderLineEntity l
      WHERE l.order.id IN (SELECT o.id FROM OrderEntity o
                           WHERE o.status = com.shoesshop.com.enums.OrderStatus.CART
                             AND o.lastSeenAt < :cutoff
                             AND NOT EXISTS (SELECT 1 FROM OrderLineEntity h
                                         WHERE h.order = o AND h.reservedQuantity > 0))
      """)
  int deleteAbandonedCartLines(@Param("cutoff") Instant cutoff);

  @Modifying
  @Query("""
      DELETE FROM OrderEntity o
      WHERE o.status = com.shoesshop.com.enums.OrderStatus.CART
        AND o.lastSeenAt < :cutoff
        AND NOT EXISTS (SELECT 1 FROM OrderLineEntity h
                                         WHERE h.order = o AND h.reservedQuantity > 0)
      """)
  int deleteAbandonedCarts(@Param("cutoff") Instant cutoff);
}
