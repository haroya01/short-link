package com.example.short_link.federation.application.delivery;

import com.example.short_link.federation.domain.FederationDeliveryEntity;
import com.example.short_link.federation.domain.repository.FederationDeliveryRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class DeliveryQueue {

  private final FederationDeliveryRepository deliveries;
  private final Clock clock;

  @Autowired
  public DeliveryQueue(FederationDeliveryRepository deliveries) {
    this(deliveries, Clock.systemUTC());
  }

  DeliveryQueue(FederationDeliveryRepository deliveries, Clock clock) {
    this.deliveries = deliveries;
    this.clock = clock;
  }

  public int enqueue(
      Long signerUserId, String activityId, String body, Collection<String> inboxes) {
    int added = 0;
    for (String inbox : new LinkedHashSet<>(inboxes)) {
      String host = host(inbox);
      if (host == null) {
        continue;
      }
      String key = dedupeKey(activityId, inbox);
      if (deliveries.existsByDedupeKey(key)) {
        continue;
      }
      try {
        deliveries.saveAndFlush(
            new FederationDeliveryEntity(
                key, inbox, host, signerUserId, activityId, body, clock.instant()));
        added++;
      } catch (DataIntegrityViolationException alreadyQueued) {
        // a concurrent enqueue of the same activity to the same inbox won the unique key
      }
    }
    return added;
  }

  static String dedupeKey(String activityId, String inbox) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest((activityId + "\n" + inbox).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static String host(String inbox) {
    try {
      String host = URI.create(inbox).getHost();
      return host == null ? null : host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
