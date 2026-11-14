package com.shoesshop.com.handler;

import com.shoesshop.com.exception.CartCheckoutException;
import com.shoesshop.com.exception.ItemNotFoundException;
import com.shoesshop.com.exception.ItemStockNumberException;
import com.shoesshop.com.exception.OrderNotFoundException;
import com.shoesshop.com.exception.PaymentConflictException;
import com.shoesshop.com.exception.PaymentNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the shop's exceptions into proper HTTP statuses for every controller. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler({ItemStockNumberException.class, CartCheckoutException.class,
      PaymentConflictException.class})
  public ProblemDetail conflict(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler({OrderNotFoundException.class, ItemNotFoundException.class,
      PaymentNotFoundException.class})
  public ProblemDetail notFound(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  /** e.g. deleting an item that is already part of an order. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail dataConflict(DataIntegrityViolationException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
        "The request conflicts with existing data (it is still referenced by other records)");
  }
}
