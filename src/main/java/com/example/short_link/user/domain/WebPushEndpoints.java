package com.example.short_link.user.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

// The push services browsers subscribe through: Chromium (FCM), Firefox (Mozilla autopush), Edge
// (WNS) and Safari (Apple). The server POSTs to a stored endpoint, so nothing else is kept.
public final class WebPushEndpoints {

  private static final Set<String> HOSTS =
      Set.of("fcm.googleapis.com", "android.googleapis.com", "web.push.apple.com");
  private static final Set<String> HOST_SUFFIXES =
      Set.of(".push.services.mozilla.com", ".notify.windows.com");

  private WebPushEndpoints() {}

  public static boolean isPushService(String endpoint) {
    if (endpoint == null) {
      return false;
    }
    URI uri;
    try {
      uri = new URI(endpoint);
    } catch (URISyntaxException e) {
      return false;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getRawUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
        || uri.getHost() == null) {
      return false;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    return HOSTS.contains(host) || HOST_SUFFIXES.stream().anyMatch(host::endsWith);
  }
}
