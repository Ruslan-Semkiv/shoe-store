package com.shoesshop.com.controller;

import com.shoesshop.com.dto.request.OrderRequestDto;
import com.shoesshop.com.dto.response.OrderResponseDto;
import com.shoesshop.com.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/v1/orders")
@RestController
public class OrderController {

  private final OrderService orderService;

  @GetMapping
  public Page<OrderResponseDto> getAllOrder(@Valid @NotNull Pageable pageable) {
    return orderService.getAll(pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderResponseDto> getOrder(@PathVariable @NotNull UUID id) {
    return ResponseEntity.ok(orderService.getById(id));
  }

  @PostMapping
  public ResponseEntity<OrderResponseDto> createOrder(
      @RequestBody @Valid OrderRequestDto requestDto) {
    OrderResponseDto responseDto = orderService.createOrder(requestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> removeOrder(@PathVariable("id") @NotNull UUID orderId) {
    orderService.removeOrder(orderId);
    return ResponseEntity.noContent().build();
  }
}
