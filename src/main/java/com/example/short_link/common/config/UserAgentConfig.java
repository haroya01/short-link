package com.example.short_link.common.config;

import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserAgentConfig {

  @Bean
  public UserAgentAnalyzer userAgentAnalyzer() {
    return UserAgentAnalyzer.newBuilder().hideMatcherLoadStats().withCache(10_000).build();
  }
}
