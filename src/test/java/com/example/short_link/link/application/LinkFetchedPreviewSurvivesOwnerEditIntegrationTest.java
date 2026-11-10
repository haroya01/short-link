package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class LinkFetchedPreviewSurvivesOwnerEditIntegrationTest {

  @Autowired private LinkRepository links;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;

  private UserEntity owner;
  private LinkEntity link;

  @BeforeEach
  void anOwnedLink() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "d" + suffix, owner.getId(), null));
  }

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
  }

  @Test
  void anOwnerEditCommittedAfterAFetchKeepsTheFetchedPreview() {
    TransactionTemplate ownerEdit = new TransactionTemplate(transactions);
    TransactionTemplate fetch = new TransactionTemplate(transactions);
    fetch.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    ownerEdit.executeWithoutResult(
        status -> {
          LinkEntity editing = links.findById(link.getId()).orElseThrow();
          editing.updateNote("메모");
          fetch.executeWithoutResult(
              inner ->
                  links.recordOgFetched(
                      link.getId(),
                      "가져온 제목",
                      "가져온 설명",
                      "https://img.example.com/a.png",
                      Instant.parse("2026-11-10T00:00:00Z")));
        });

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT note, og_title, og_fetch_status, og_fetch_attempts FROM link WHERE id = ?",
            link.getId());
    assertThat(row)
        .containsEntry("note", "메모")
        .containsEntry("og_title", "가져온 제목")
        .containsEntry("og_fetch_status", "OK")
        .containsEntry("og_fetch_attempts", 1);
  }
}
