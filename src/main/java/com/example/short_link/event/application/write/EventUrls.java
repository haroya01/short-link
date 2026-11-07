package com.example.short_link.event.application.write;

import com.example.short_link.common.security.OutboundUrlPolicy;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;

final class EventUrls {

  private EventUrls() {}

  static void requireAllowed(OutboundUrlPolicy outbound, String... urls) {
    for (String url : urls) {
      if (url != null && !url.isBlank() && !outbound.allows(url.trim())) {
        throw new EventException(EventErrorCode.MALICIOUS_URL);
      }
    }
  }
}
