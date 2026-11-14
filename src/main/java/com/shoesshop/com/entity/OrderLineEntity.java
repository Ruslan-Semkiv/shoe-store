package com.shoesshop.com.entity;

import com.shoesshop.com.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "order_line", schema = "shop")
@Entity
public class OrderLineEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "quantity")
  private Integer quantity;

  @Column(name = "unit_price")
  private Long unitPrice;

  @Column(name = "currency")
  @Enumerated(value = EnumType.STRING)
  private Currency currency;

  /**
   * Units locked for this line right now, counted in {@link ItemEntity#getReservedUnits()}.
   * Lower than quantity = the rest sold out meanwhile; 0 once the hold expired or the order is
   * placed.
   */
  @Column(name = "reserved_quantity")
  private Integer reservedQuantity = 0;

  /** The lock runs out at this point unless the browser pings /heartbeat. */
  @Column(name = "reserved_until")
  private Instant reservedUntil;

  /** Heartbeats may push reservedUntil up to this point, never beyond. */
  @Column(name = "reserved_max_until")
  private Instant reservedMaxUntil;

  @ManyToOne
  @JoinColumn(name = "item_id")
  private ItemEntity item;

  @ManyToOne(fetch = FetchType.LAZY)
  private OrderEntity order;

}