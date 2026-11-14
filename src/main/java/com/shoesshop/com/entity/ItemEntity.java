package com.shoesshop.com.entity;

import com.shoesshop.com.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "items", schema = "shop")
@Entity
public class ItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name")
  private String name;

  @Column(name = "colour")
  private String colour;

  @Column(name = "model")
  private String model;

  @Column(name = "units_number")
  private Long unitsNumber;

  @Column(name = "size")
  private Short size;

  /** Units locked in customers' carts (order_line.reserved_quantity); available = unitsNumber - reservedUnits. */
  @Column(name = "reserved_units")
  private Long reservedUnits = 0L;

  /** In minor units (cents) of {@link #currency}; copied to the line at checkout. */
  @Column(name = "price")
  private Long price;

  /** The price's currency; order lines take it from here. */
  @Column(name = "currency")
  @Enumerated(value = EnumType.STRING)
  private Currency currency;

}
