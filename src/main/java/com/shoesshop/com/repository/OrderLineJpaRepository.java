package com.shoesshop.com.repository;

import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.repository.common.ValidatorJpaRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderLineJpaRepository extends ValidatorJpaRepository<OrderLineEntity, UUID> {

  default OrderLineEntity getOrElseThrow(UUID id) {
    return getOrElseThrow(id,"There is not order line, with the id -");
  }

  Page<OrderLineEntity> findAllByOrderId(UUID orderId, Pageable pageable);

  List<OrderLineEntity> findAllByOrderId(UUID orderId);

  /**
   * SELECT ... FOR UPDATE, so the release job can't free a line's hold while we change it.
   * Always read lines through this before changing their reservedQuantity.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<OrderLineEntity> findAllLockedByOrderId(UUID orderId);

  /** Heartbeat: push expiry forward, but never past reservedMaxUntil; expired holds stay dead. */
  @Modifying
  @Query("""
      UPDATE OrderLineEntity l
      SET l.reservedUntil = CASE WHEN l.reservedMaxUntil < :newExpiry
                                 THEN l.reservedMaxUntil ELSE :newExpiry END
      WHERE l.order.id = :orderId AND l.reservedQuantity > 0 AND l.reservedUntil > :now
      """)
  int extendHolds(@Param("orderId") UUID orderId,
                  @Param("newExpiry") Instant newExpiry,
                  @Param("now") Instant now);

  /**
   * Clears the hold of up to {@code batch} expired lines and gives their units back to
   * shop.items in one statement. The lines themselves stay, so a returning customer finds them.
   * SKIP LOCKED lets several app instances run the job at once, and skips lines a request is
   * currently changing.
   */
  @Modifying
  @Query(nativeQuery = true, value = """
      WITH expired AS (
          UPDATE shop.order_line l
          SET reserved_quantity = 0, reserved_until = NULL, reserved_max_until = NULL
          FROM (SELECT id, reserved_quantity AS qty FROM shop.order_line
                WHERE reserved_quantity > 0 AND reserved_until < :now
                ORDER BY reserved_until
                LIMIT :batch
                FOR UPDATE SKIP LOCKED) e
          WHERE l.id = e.id
          RETURNING l.item_id, e.qty
      ), totals AS (
          SELECT item_id, SUM(qty) AS qty FROM expired GROUP BY item_id
      )
      UPDATE shop.items i
      SET reserved_units = GREATEST(i.reserved_units - t.qty, 0)
      FROM totals t
      WHERE i.id = t.item_id
      """)
  int releaseExpired(@Param("now") Instant now, @Param("batch") int batch);
}
