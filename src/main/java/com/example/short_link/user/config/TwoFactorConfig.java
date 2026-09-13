package com.example.short_link.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class TwoFactorConfig {

  @Bean
  PasswordEncoder recoveryCodePasswordEncoder() {
    return new BCryptPasswordEncoder(10);
  }
}
