package com.example.short_link.event.application.read;

import com.example.short_link.event.domain.EventRegistrationEntity;
import java.util.Map;

final class RegistrationChannel {

  private RegistrationChannel() {}

  static String labelFor(EventRegistrationEntity registration, Map<Long, String> channelByLinkId) {
    if (registration.getLinkId() != null) {
      String label = channelByLinkId.get(registration.getLinkId());
      if (label != null) return label;
    }
    if (registration.getClientApp() != null) return registration.getClientApp();
    return registration.getReferrerHost();
  }
}
