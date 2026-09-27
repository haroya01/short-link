package com.example.short_link.link.access.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class LinkProtectionConfig {

  @Bean
  PasswordEncoder linkPasswordEncoder() {
    return new BCryptPasswordEncoder(10);
  }
}
