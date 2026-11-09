package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.application.write.UpdateLinkCommand;
import com.example.short_link.link.application.write.UpdateLinkUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class LinkOgFetchFollowsTheCurrentUrlTest {
  private static final String OLD_URL = "https://example.com/old-article";
  private static final String NEW_URL = "https://example.com/new-article";

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private LinkOgFetchService ogFetch;
  @Autowired private UpdateLinkUseCase updateLink;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private OgScraper scraper;

  private Long ownerId;

  @AfterEach
  void deleteCommittedRows() {
    jdbc.update("DELETE FROM link WHERE user_id = ?", ownerId);
    jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
  }

  @Test
  void aFetchForTheOldUrlThatFinishesAfterTheOwnerChangedItRecordsNothing() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ownerId = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix)).getId();
    ShortCode code =
        links.save(new LinkEntity(OLD_URL, "u" + suffix, ownerId, null)).getShortCode();
    when(scraper.fetch(NEW_URL)).thenReturn(OgMetadata.empty());
    when(scraper.fetch(OLD_URL))
        .thenAnswer(
            invocation -> {
              updateLink.execute(new UpdateLinkCommand(ownerId, code, NEW_URL, null, null, null));
              return new OgMetadata("Old title", "Old desc", "https://example.com/old.png");
            });

    ogFetch.refresh(code, OLD_URL);

    LinkEntity link = links.findByShortCode(code).orElseThrow();
    assertThat(link.getOriginalUrl()).isEqualTo(NEW_URL);
    assertThat(link.getOgTitle()).isNull();
    assertThat(link.getOgImage()).isNull();
    assertThat(
            jdbc.queryForList(
                "SELECT og_title FROM link_og_metadata WHERE link_id = ?",
                String.class,
                link.getId()))
        .doesNotContain("Old title");
  }
}
