package com.example.short_link.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AddressChangeHistory {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final List<String> LINK_ACTIONS =
      List.of(
          AuditAction.LINK_UPDATED.name(),
          AuditAction.LINK_DESTINATION_ADDED.name(),
          AuditAction.LINK_DESTINATION_UPDATED.name(),
          AuditAction.LINK_DESTINATION_DELETED.name());

  private final AuditLogJpaRepository auditLog;
  private final AuditLogProperties properties;
  private final Clock clock;

  @Transactional(readOnly = true)
  public AddressChanges ofLink(String shortCode) {
    Instant since = clock.instant().minus(Duration.ofDays(properties.retentionDays()));
    long urlChanges = 0;
    long destinationChanges = 0;
    Long latestId = null;
    for (AuditLogJpaRepository.AddressChangeCount count :
        auditLog.countAddressChanges("link", shortCode, LINK_ACTIONS, since)) {
      if (AuditAction.LINK_UPDATED.name().equals(count.getAction())) {
        urlChanges += count.getChanges();
      } else {
        destinationChanges += count.getChanges();
      }
      if (latestId == null || count.getLatestId() > latestId) {
        latestId = count.getLatestId();
      }
    }
    AddressChanges.Latest latest =
        latestId == null ? null : auditLog.findById(latestId).map(this::latest).orElse(null);
    return new AddressChanges(properties.retentionDays(), urlChanges, destinationChanges, latest);
  }

  private AddressChanges.Latest latest(AuditLogEntity row) {
    JsonNode metadata = metadata(row.getMetadata());
    return new AddressChanges.Latest(
        row.getAction(),
        row.getOccurredAt(),
        text(metadata, AddressChange.FROM_HOST),
        text(metadata, AddressChange.TO_HOST));
  }

  private static JsonNode metadata(String json) {
    try {
      return OBJECT_MAPPER.readTree(json == null ? "{}" : json);
    } catch (JsonProcessingException unreadable) {
      return OBJECT_MAPPER.createObjectNode();
    }
  }

  private static String text(JsonNode metadata, String key) {
    JsonNode value = metadata.get(key);
    return value == null || !value.isTextual() ? null : value.asText();
  }

  public record AddressChanges(
      long retentionDays, long urlChanges, long destinationChanges, Latest latest) {

    public record Latest(String action, Instant at, String fromHost, String toHost) {}
  }
}
