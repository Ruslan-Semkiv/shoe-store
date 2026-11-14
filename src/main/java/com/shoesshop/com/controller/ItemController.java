package com.shoesshop.com.controller;

import com.shoesshop.com.dto.request.ItemRequestDto;
import com.shoesshop.com.dto.response.ItemResponseDto;
import com.shoesshop.com.service.ItemService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/v1/items")
@RestController
public class ItemController {

  private final ItemService itemService;

  @GetMapping
  public Page<ItemResponseDto> getItems(@Valid @NotNull Pageable pageable) {
    return itemService.getAll(pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ItemResponseDto> getItemById(@PathVariable @NotNull UUID id) {
    ItemResponseDto responseDto = itemService.getById(id);
    return ResponseEntity.ok(responseDto);
  }

  @PostMapping
  public ResponseEntity<ItemResponseDto> createItem(@RequestBody @Valid @NotNull ItemRequestDto requestDto) {
    ItemResponseDto result = itemService.createItem(requestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @PutMapping("/{id}")
  public ResponseEntity<ItemResponseDto> updateItem(@PathVariable @NotNull UUID id,
                                                    @RequestBody @Valid @NotNull ItemRequestDto requestDto) {
    ItemResponseDto responseDto = itemService.updateById(id, requestDto);
    return ResponseEntity.accepted().body(responseDto);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteItem(@PathVariable @NotNull UUID id) {
    itemService.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
