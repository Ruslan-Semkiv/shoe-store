package com.shoesshop.com.mapper;

import com.shoesshop.com.dto.request.OrderLineRequestDto;
import com.shoesshop.com.dto.response.OrderLineResponseDto;
import com.shoesshop.com.entity.OrderLineEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderLineMapper {

  @Mapping(source = "item.id", target = "itemId")
  @Mapping(source = "order.id", target = "orderId")
  OrderLineResponseDto toDto (OrderLineEntity entity);
  OrderLineEntity toEntity(OrderLineRequestDto dto);

}
