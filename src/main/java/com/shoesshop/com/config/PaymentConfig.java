package com.shoesshop.com.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(PaymentProperties.class)
@Configuration
public class PaymentConfig {
}
