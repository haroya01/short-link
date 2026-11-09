package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class LinkViewCountSurvivesOwnerEditTest {

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private IncrementViewCountUseCase incrementViewCount;
  @Autowired private LinkProtectionService protection;
  @Autowired private TransactionTemplate transaction;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;

  @AfterEach
  void deleteCommittedRows() {
    jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void aVisitCountedWhileTheOwnerEditsTheLinkStillSpendsTheLimit() {
    ShortCode code = linkLimitedToOneVisit();

    transaction.executeWithoutResult(
        status -> {
          LinkEntity loaded = links.findByShortCode(code).orElseThrow();
          CompletableFuture.runAsync(
                  () -> incrementViewCount.execute(new IncrementViewCountCommand(loaded.linkId())))
              .join();
          loaded.updateNote("edited while a visitor opened it");
        });

    assertThat(viewCount(code)).isEqualTo(1);
    assertThat(incrementViewCount.execute(new IncrementViewCountCommand(linkId(code)))).isZero();
  }

  private ShortCode linkLimitedToOneVisit() {
    String tag = UUID.randomUUID().toString();
    userId = users.save(new UserEntity(tag + "@x.com", "google", tag)).getId();
    ShortCode code = new ShortCode("vc" + tag.replace("-", "").substring(0, 8));
    links.save(new LinkEntity("https://example.com/", code, userId, null));
    protection.update(userId, code, null, 1);
    return code;
  }

  private int viewCount(ShortCode code) {
    return jdbc.queryForObject(
        "SELECT view_count FROM link WHERE short_code = ?", Integer.class, code.value());
  }

  private LinkId linkId(ShortCode code) {
    return links.findByShortCode(code).orElseThrow().linkId();
  }
}
