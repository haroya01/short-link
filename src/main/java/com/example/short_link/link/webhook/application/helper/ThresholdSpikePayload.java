package com.example.short_link.link.webhook.application.helper;

import com.example.short_link.link.domain.ShortCode;
import java.util.LinkedHashMap;
import java.util.Map;

public record ThresholdSpikePayload(
    ShortCode shortCode, String window, long clicks, int threshold, String topReferrer) {

  public Map<String, Object> toJsonMap() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "spike_alert");
    root.put("shortCode", shortCode.value());
    root.put("window", window);
    root.put("clicks", clicks);
    root.put("threshold", threshold);
    root.put("topReferrer", topReferrer);
    return root;
  }
}
