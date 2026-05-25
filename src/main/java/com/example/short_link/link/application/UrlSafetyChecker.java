package com.example.short_link.link.application;

import com.example.short_link.common.config.SafeBrowsingProperties;
import com.example.short_link.link.application.helper.ReferrerNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlSafetyChecker {

  private final SafeBrowsingClient client;
  private final SafeBrowsingProperties properties;
  private final MeterRegistry meterRegistry;

  public boolean isSafe(String url) {
    if (url == null || url.isBlank()) {
      return true;
    }
    if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
      return true;
    }
    String cacheKey = ReferrerNormalizer.normalize(url);
    if (cacheKey == null) {
      return true;
    }
    try {
      boolean safe = client.isSafeForKey(cacheKey, url);
      meterRegistry
          .counter("safe_browsing.check", "result", safe ? "safe" : "malicious")
          .increment();
      return safe;
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
      meterRegistry.counter("safe_browsing.check", "result", "auth_error").increment();
      log.error("safe browsing api key invalid or unauthorized; allowing through", e);
      return true;
    } catch (Exception e) {
      meterRegistry.counter("safe_browsing.check", "result", "error").increment();
      log.warn("safe browsing check failed; allowing through", e);
      return true;
    }
  }
}
