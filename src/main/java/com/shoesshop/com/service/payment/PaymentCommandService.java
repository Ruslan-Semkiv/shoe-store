package com.shoesshop.com.service.payment;

import com.shoesshop.com.dto.response.PaymentResponseDto;
import com.shoesshop.com.entity.OrderEntity;
import com.shoesshop.com.entity.OrderLineEntity;
import com.shoesshop.com.entity.PaymentEntity;
import com.shoesshop.com.enums.PaymentType;
import com.shoesshop.com.exception.OrderNotFoundException;
import com.shoesshop.com.repository.OrderJpaRepository;
import com.shoesshop.com.repository.OrderLineJpaRepository;
import com.shoesshop.com.service.PaymentTxService;
import com.shoesshop.com.service.payment.check.PaymentCheck;
import com.shoesshop.com.service.payment.check.PaymentContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "Pay" of a placed order, step by step:
 *
 * <ol>
 *   <li>lock the order and run every {@link PaymentCheck} in order; the first failing one stops
 *       the payment with 409</li>
 *   <li>save the payment as PROCESSING</li>
 *   <li>charge it on the provider and save the outcome ({@link PaymentExecutor})</li>
 * </ol>
 *
 * <p>Steps 1 and 2 share one short transaction: the order stays locked from the first check
 * until the payment row exists, so two "Pay" clicks can't both pass the checks. Step 3 runs
 * after it commits, so the provider call never holds a DB connection or the lock.
 */
@RequiredArgsConstructor
@Service
public class PaymentCommandService {

  private final OrderJpaRepository orderRepository;
  private final OrderLineJpaRepository orderLineRepository;
  private final List<PaymentCheck> checks; // in @Order sequence
  private final PaymentTxService paymentTxService;
  private final PaymentExecutor paymentExecutor;
  private final TransactionTemplate transaction;

  public PaymentResponseDto pay(UUID orderId, PaymentType paymentType) {

    PaymentEntity payment = transaction.execute(tx -> {
      PaymentContext context = loadContext(orderId, paymentType);
      checks.forEach(check -> check.verify(context));

      return paymentTxService.start(context);
    });

    PaymentEntity paymentResult = paymentExecutor.execute(payment);

    return PaymentResponseDto.from(paymentResult);
  }

  private PaymentContext loadContext(UUID orderId, PaymentType paymentType) {
    OrderEntity order = orderRepository.findLockedById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("There is no order with the provided id - " + orderId));

    List<OrderLineEntity> orderLines = orderLineRepository.findAllByOrderId(orderId);
    return new PaymentContext(order, orderLines, paymentType);
  }
}
