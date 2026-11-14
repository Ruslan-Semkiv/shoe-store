package com.shoesshop.com.repository;

import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.enums.PaymentStatus;
import com.shoesshop.com.repository.common.ValidatorJpaRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentJpaRepository extends ValidatorJpaRepository<PaymentEntity, UUID> {

  /** SELECT ... FOR UPDATE: the job, the status check and the paying request can't race. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PaymentEntity> findLockedById(UUID id);

  /** Is there a payment of this order that isn't declined (running, pending or done)? */
  boolean existsByOrderIdAndStatusNot(UUID orderId, PaymentStatus status);

  boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

  /**
   * Payments due for the retry job: RETRY_PENDING whose time came, and PROCESSING whose lease
   * ran out (the app died mid-call). SKIP LOCKED lets several app instances run the job at once.
   */
  @Query(nativeQuery = true, value = """
      SELECT * FROM shop.payment
      WHERE status IN ('RETRY_PENDING', 'PROCESSING') AND next_attempt_at <= :now
      ORDER BY next_attempt_at
      LIMIT :batch
      FOR UPDATE SKIP LOCKED
      """)
  List<PaymentEntity> findDueLocked(@Param("now") Instant now, @Param("batch") int batch);
}
