package com.example.short_link.common.audit;

import com.example.short_link.common.net.BrowserHost;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Addresses can carry personal tokens in their path or query, so a change keeps only the hosts.
public final class AddressChange {

  public static final String URL_CHANGED = "urlChanged";
  public static final String FROM_HOST = "fromHost";
  public static final String TO_HOST = "toHost";

  private AddressChange() {}

  public static Map<String, Object> between(String before, String after) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    boolean changed = !Objects.equals(before, after);
    metadata.put(URL_CHANGED, changed);
    if (changed) {
      putHost(metadata, FROM_HOST, before);
      putHost(metadata, TO_HOST, after);
    }
    return metadata;
  }

  private static void putHost(Map<String, Object> metadata, String key, String url) {
    String host = BrowserHost.of(url);
    if (host != null) {
      metadata.put(key, host);
    }
  }
}
