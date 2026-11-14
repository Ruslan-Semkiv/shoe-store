package com.shoesshop.com.entity;

import com.shoesshop.com.enums.Currency;
import com.shoesshop.com.enums.PaymentStatus;
import com.shoesshop.com.enums.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One payment try of a placed order. Its id is the Idempotency-Key sent to the provider. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment", schema = "shop")
@Entity
public class PaymentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id")
  private UUID orderId;

  /** Minor units (cents). */
  @Column(name = "amount")
  private Long amount;

  @Column(name = "currency")
  @Enumerated(value = EnumType.STRING)
  private Currency currency;

  @Column(name = "payment_type")
  @Enumerated(value = EnumType.STRING)
  private PaymentType paymentType;

  @Column(name = "status")
  @Enumerated(value = EnumType.STRING)
  private PaymentStatus status;

  @Column(name = "external_charge_id")
  private String externalChargeId;

  /** How many times PaymentRetryJob has picked it up. */
  @Column(name = "job_attempts")
  private Integer jobAttempts = 0;

  /** RETRY_PENDING: when the job tries again. PROCESSING: when the running call's lease ends. */
  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
