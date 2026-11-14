package com.shoesshop.com.config;

import com.shoesshop.com.feign.PaymentProviderClient;
import com.shoesshop.com.feign.error.ExponentialRetryer;
import com.shoesshop.com.feign.error.PaymentErrorDecoder;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import feign.jackson3.Jackson3Decoder;
import feign.jackson3.Jackson3Encoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class WebConfig {

  @Bean(name = "paymentProviderClient")
  public PaymentProviderClient paymentProviderClient(
      PaymentProperties properties,
      JsonMapper jsonMapper
  ) {
    Retryer retryer = new ExponentialRetryer(
        properties.retryBaseDelay(), properties.retryMultiplier(), properties.maxRetries());

    return Feign.builder()
        .encoder(new Jackson3Encoder(jsonMapper))
        .decoder(new Jackson3Decoder(jsonMapper))
        .errorDecoder(new PaymentErrorDecoder())
        .options(new Request.Options(
            properties.connectTimeout(), properties.readTimeout(), true))
        .retryer(retryer)
        .target(PaymentProviderClient.class, properties.baseUrl());
  }

  @Bean(name = "singleCallPaymentClient")
  public PaymentProviderClient singleCallPaymentClient(
      PaymentProperties properties,
      JsonMapper jsonMapper
  ) {

    return Feign.builder()
        .encoder(new Jackson3Encoder(jsonMapper))
        .decoder(new Jackson3Decoder(jsonMapper))
        .errorDecoder(new PaymentErrorDecoder())
        .options(new Request.Options(
            properties.connectTimeout(), properties.readTimeout(), true))
        .retryer(Retryer.NEVER_RETRY)
        .target(PaymentProviderClient.class, properties.baseUrl());
  }
}
