package com.shoesshop.com.mapper;

import com.shoesshop.com.dto.response.OrderResponseDto;
import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import java.util.UUID;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {

  OrderResponseDto toDto(OrderEntity entity);

  /** OrderResponseDto lists its lines by id. */
  default UUID orderLineId(OrderLineEntity line) {
    return line.getId();
  }
}
