package com.shoesshop.com.service;

import com.shoesshop.com.dto.request.ItemRequestDto;
import com.shoesshop.com.dto.response.ItemResponseDto;
import com.shoesshop.com.entity.ItemEntity;
import com.shoesshop.com.exception.ItemNotFoundException;
import com.shoesshop.com.exception.ItemStockNumberException;
import com.shoesshop.com.mapper.ItemMapper;
import com.shoesshop.com.repository.ItemJpaRepository;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ItemService {

  private final ItemJpaRepository itemRepository;
  private final ItemMapper mapper;

  public Page<ItemResponseDto> getAll(Pageable pageable) {
    return itemRepository.findAll(pageable).map(mapper::toDto);
  }

  public ItemResponseDto getById(UUID id) {
    ItemEntity entity = itemRepository.getOrElseThrow(id);
    return mapper.toDto(entity);
  }

  public ItemResponseDto createItem(ItemRequestDto dto) {
    ItemEntity entity = mapper.toEntity(dto);
    return mapper.toDto(
        itemRepository.save(entity)
    );
  }

  @Transactional
  public ItemResponseDto updateById(UUID id, ItemRequestDto dto) {
    // locked: carts reserve this row concurrently, and the save writes reserved_units back
    ItemEntity entity = itemRepository.findLockedById(id).orElseThrow(
        () -> new ItemNotFoundException("There is not item, with the id -" + id));
    if (dto.unitsNumber() < entity.getReservedUnits()) {
      throw new ItemStockNumberException("The item [" + id + "] has " + entity.getReservedUnits()
          + " units held in carts, units number can't go below that");
    }
    entity.setName(dto.name());
    entity.setColour(dto.colour());
    entity.setModel(dto.model());
    entity.setSize(dto.size());
    entity.setUnitsNumber(dto.unitsNumber());
    entity.setPrice(dto.price());
    entity.setCurrency(dto.currency());

    return mapper.toDto(itemRepository.save(entity));
  }

  @Transactional
  public void deleteById(UUID id) {
    ItemEntity entity = itemRepository.getOrElseThrow(id);

    itemRepository.delete(entity);
  }
}
