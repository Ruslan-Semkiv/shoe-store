package com.shoesshop.com.repository;

import com.shoesshop.com.entity.ItemEntity;
import com.shoesshop.com.repository.common.ValidatorJpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemJpaRepository extends ValidatorJpaRepository<ItemEntity, UUID> {
  default ItemEntity getOrElseThrow(UUID id) {
    return getOrElseThrow(id,"There is not item, with the id -");
  }

  /** SELECT ... FOR UPDATE: tryReserve/release on this item wait until the update commits. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<ItemEntity> findLockedById(UUID id);

  /**
   * Atomic "check and reserve": Postgres locks the row and re-checks the WHERE, so two customers
   * can never reserve the same last pair. Returns 1 on success, 0 if not enough stock.
   */
  @Modifying
  @Query("""
      UPDATE ItemEntity i SET i.reservedUnits = i.reservedUnits + :qty
      WHERE i.id = :id AND i.unitsNumber - i.reservedUnits >= :qty
      """)
  int tryReserve(@Param("id") UUID id, @Param("qty") long qty);

  @Modifying
  @Query("""
      UPDATE ItemEntity i SET i.reservedUnits = i.reservedUnits - :qty
      WHERE i.id = :id AND i.reservedUnits >= :qty
      """)
  int release(@Param("id") UUID id, @Param("qty") long qty);

  /**
   * Checkout: held units become sold units. Takes them out of both the stock and the reserved
   * counter in one atomic update. Returns 0 if the counters don't add up (nothing is changed).
   */
  @Modifying
  @Query("""
      UPDATE ItemEntity i
      SET i.unitsNumber = i.unitsNumber - :qty, i.reservedUnits = i.reservedUnits - :qty
      WHERE i.id = :id AND i.reservedUnits >= :qty AND i.unitsNumber >= :qty
      """)
  int commitReserved(@Param("id") UUID id, @Param("qty") long qty);

  /** Sold units come back to stock (a placed order was deleted). */
  @Modifying
  @Query("UPDATE ItemEntity i SET i.unitsNumber = i.unitsNumber + :qty WHERE i.id = :id")
  int restock(@Param("id") UUID id, @Param("qty") long qty);
}
