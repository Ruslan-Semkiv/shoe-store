package com.shoesshop.com.controller;

import com.shoesshop.com.dto.request.OrderLineRequestDto;
import com.shoesshop.com.dto.response.OrderLineResponseDto;
import com.shoesshop.com.service.OrderLineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/v1/order-items")
@RestController
public class OrderLineController {

  private final OrderLineService orderLineService;

  @GetMapping("/orders/{orderId}")
  public Page<OrderLineResponseDto> getAllByOrderId(@PathVariable @NotNull UUID orderId,
                                                    @NotNull Pageable pageable) {
    return orderLineService.getAllByOrderId(orderId, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderLineResponseDto> getById(@PathVariable @NotNull UUID id) {
    OrderLineResponseDto responseDto = orderLineService.getOrderLine(id);
    return ResponseEntity.ok(responseDto);
  }

  @PostMapping("/orders/{orderId}")
  public ResponseEntity<OrderLineResponseDto> createOrderLine(
      @PathVariable @NotNull UUID orderId,
      @RequestBody @Valid OrderLineRequestDto requestDto) {

    OrderLineResponseDto responseDto = orderLineService.createOrderLine(orderId, requestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
  }
}