package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.application.write.AddDestinationUseCase;
import com.example.short_link.link.destination.application.write.DeleteDestinationUseCase;
import com.example.short_link.link.destination.application.write.UpdateDestinationUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class LinkAddressChangeAuditIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @MockitoBean private UrlSafetyChecker safeBrowsing;
  @Autowired private UpdateLinkUseCase updateLink;
  @Autowired private AddDestinationUseCase addDestination;
  @Autowired private UpdateDestinationUseCase updateDestination;
  @Autowired private DeleteDestinationUseCase deleteDestination;
  @Autowired private LinkRepository links;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final String code = "ac" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  private Long ownerId;

  @BeforeEach
  void everyAddressIsSafe() {
    when(safeBrowsing.isSafe(anyString())).thenReturn(true);
  }

  @AfterEach
  void deleteCommittedRows() {
    jdbc.update("DELETE FROM audit_log WHERE target_type = 'link' AND target_id = ?", code);
    jdbc.update(
        "DELETE FROM link_destination WHERE link_id IN (SELECT id FROM link WHERE short_code = ?)",
        code);
    jdbc.update("DELETE FROM link WHERE short_code = ?", code);
    if (ownerId != null) {
      jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
    }
  }

  @Test
  void everyAddressChangeKeepsTheHostsItMovedBetweenAndNeverTheAddress() throws Exception {
    String handle = "audit" + code;
    ownerId = users.save(new UserEntity(handle + "@example.com", "google", handle)).getId();
    links.save(new LinkEntity("https://first.example/welcome?invite=SECRET1", code, ownerId, null));
    ShortCode shortCode = new ShortCode(code);

    updateLink.execute(
        new UpdateLinkCommand(
            ownerId, shortCode, "https://second.example/pay?token=SECRET2", null, null, null));
    DestinationSummary branch =
        addDestination.execute(
            ownerId, shortCode, "https://branch.example/a?t=SECRET3", 10, "B", "KR", null, null);
    updateDestination.execute(
        ownerId,
        shortCode,
        branch.id(),
        "https://moved.example/b?t=SECRET4",
        null,
        null,
        null,
        null,
        null,
        null);
    updateDestination.execute(
        ownerId, shortCode, branch.id(), null, 20, null, null, null, null, null);
    deleteDestination.execute(ownerId, shortCode, branch.id());

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT action, actor_user_id, metadata FROM audit_log"
                + " WHERE target_type = 'link' AND target_id = ? ORDER BY id",
            code);
    String destination = String.valueOf(branch.id());
    assertThat(rows)
        .extracting(row -> row.get("action"))
        .containsExactly(
            "LINK_UPDATED",
            "LINK_DESTINATION_ADDED",
            "LINK_DESTINATION_UPDATED",
            "LINK_DESTINATION_UPDATED",
            "LINK_DESTINATION_DELETED");
    assertThat(rows)
        .extracting(row -> ((Number) row.get("actor_user_id")).longValue())
        .containsOnly(ownerId);
    assertThat(rows)
        .extracting(row -> fields(row.get("metadata")))
        .containsExactly(
            Map.of(
                "urlChanged", "true",
                "fromHost", "first.example",
                "toHost", "second.example",
                "expiresAtChanged", "false"),
            Map.of("urlChanged", "true", "toHost", "branch.example", "destinationId", destination),
            Map.of(
                "urlChanged", "true",
                "fromHost", "branch.example",
                "toHost", "moved.example",
                "destinationId", destination),
            Map.of("urlChanged", "false", "destinationId", destination),
            Map.of(
                "urlChanged", "true", "fromHost", "moved.example", "destinationId", destination));
    assertThat(rows)
        .extracting(row -> String.valueOf(row.get("metadata")))
        .noneMatch(metadata -> metadata.contains("SECRET") || metadata.contains("/"));
  }

  private static Map<String, String> fields(Object metadata) {
    try {
      JsonNode node = JSON.readTree(String.valueOf(metadata));
      Map<String, String> fields = new LinkedHashMap<>();
      node.fields()
          .forEachRemaining(field -> fields.put(field.getKey(), field.getValue().asText()));
      return fields;
    } catch (Exception unreadable) {
      throw new AssertionError(unreadable);
    }
  }
}
