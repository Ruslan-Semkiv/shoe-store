package com.shoesshop.com.enums;

/** CART = customer is still picking items (stock is held); </br>
 * PLACED = checked out, waiting for payment; </br>
 * PAID = the payment provider charged it; </br>
 * PAYMENT_FAILED = payment gave up after every retry; the order's stock went back to the catalog */
public enum OrderStatus {

  CART, PLACED, PAID, PAYMENT_FAILED
}
