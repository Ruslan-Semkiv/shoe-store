package com.shoesshop.com.mapper;

import com.shoesshop.com.dto.request.ItemRequestDto;
import com.shoesshop.com.dto.response.ItemResponseDto;
import com.shoesshop.com.entity.ItemEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ItemMapper {

  ItemResponseDto toDto(ItemEntity itemEntity);
  ItemEntity toEntity(ItemRequestDto dto);
}
