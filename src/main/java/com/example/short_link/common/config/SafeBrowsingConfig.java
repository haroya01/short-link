package com.example.short_link.common.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SafeBrowsingConfig {

  static final String CACHE_NAME = "safebrowsing";

  @Bean
  public RestClient safeBrowsingRestClient(SafeBrowsingProperties properties) {
    Duration connect = Duration.ofSeconds(2);
    Duration read = properties.timeout() == null ? Duration.ofSeconds(2) : properties.timeout();
    HttpClient http = HttpClient.newBuilder().connectTimeout(connect).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(read);
    return RestClient.builder()
        .requestFactory(factory)
        .baseUrl("https://safebrowsing.googleapis.com")
        .build();
  }

  @Bean
  public RedisCacheManagerBuilderCustomizer safeBrowsingCacheCustomizer(
      SafeBrowsingProperties properties) {
    Duration ttl = properties.cacheTtl() == null ? Duration.ofHours(1) : properties.cacheTtl();
    return builder ->
        builder.withCacheConfiguration(
            CACHE_NAME, RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl));
  }
}
