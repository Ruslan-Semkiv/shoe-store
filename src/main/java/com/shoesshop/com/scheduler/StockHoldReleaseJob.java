package com.shoesshop.com.scheduler;

import com.shoesshop.com.config.CartProperties;
import com.shoesshop.com.repository.OrderJpaRepository;
import com.shoesshop.com.repository.OrderLineJpaRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tab closed = no more heartbeats = holds expire. This gives the units back to the catalog.
 * Safe to run on several instances at once (FOR UPDATE SKIP LOCKED in the query).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class StockHoldReleaseJob {

  private final OrderLineJpaRepository orderLineRepository;
  private final OrderJpaRepository orderRepository;
  private final CartProperties properties;

  @Transactional
  @Scheduled(fixedDelayString = "${shop.cart.release-interval:PT30S}")
  public void releaseExpiredHolds() {
    int items = orderLineRepository.releaseExpired(Instant.now(), properties.releaseBatchSize());
    if (items > 0) {
      log.info("Released expired stock holds for {} item(s)", items);
    }
  }

  @Transactional
  @Scheduled(cron = "${shop.cart.cleanup-cron:0 0 3 * * *}")
  public void deleteAbandonedCarts() {
    Instant cutoff = Instant.now().minus(properties.abandonedAfter());
    orderRepository.deleteAbandonedCartLines(cutoff);
    int carts = orderRepository.deleteAbandonedCarts(cutoff);
    if (carts > 0) {
      log.info("Deleted {} abandoned cart order(s)", carts);
    }
  }
}
