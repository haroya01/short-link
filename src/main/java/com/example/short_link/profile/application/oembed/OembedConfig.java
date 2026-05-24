package com.example.short_link.profile.application.oembed;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OembedConfig {

  @Bean
  public RestClient oembedRestClient() {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(5));
    return RestClient.builder()
        .requestFactory(factory)
        .defaultHeader("User-Agent", "kurl-oembed/1.0 (+https://kurl.me/bot)")
        .defaultHeader("Accept", "application/json")
        .build();
  }
}
