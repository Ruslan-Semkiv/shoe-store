package com.shoesshop.com.entity;

import com.shoesshop.com.enums.OrderStatus;
import com.shoesshop.com.enums.PaymentType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "\"order\"", schema = "shop")
@Entity
public class OrderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "total")
  private Long total;

  @Column(name = "payment_type")
  @Enumerated(value = EnumType.STRING)
  private PaymentType paymentType;

  @Column(name = "item_counter")
  private Integer itemCounter;

  /** CART while the customer is browsing; its id is what the browser keeps in the cart_id cookie. */
  @Column(name = "status")
  @Enumerated(value = EnumType.STRING)
  private OrderStatus status;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.REMOVE)
  private List<OrderLineEntity> orderLines;
}
