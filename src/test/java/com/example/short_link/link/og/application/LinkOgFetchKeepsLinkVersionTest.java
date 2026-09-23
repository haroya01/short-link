package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class LinkOgFetchKeepsLinkVersionTest {
  private static final String URL = "https://example.com/article";

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private LinkOgFetchService ogFetch;
  @Autowired private TransactionTemplate transaction;
  @MockitoBean private OgScraper scraper;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aDeleteThatReadTheLinkBeforeAnOgFetchStillSucceeds(boolean scraped) {
    when(scraper.fetch(anyString()))
        .thenReturn(scraped ? new OgMetadata("Title", "Desc", null) : OgMetadata.empty());
    ShortCode code = link();

    transaction.executeWithoutResult(
        status -> {
          LinkEntity loaded = links.findByShortCode(code).orElseThrow();
          CompletableFuture.runAsync(() -> ogFetch.fetchAfterCommit(code, URL)).join();
          links.delete(loaded);
        });

    assertThat(links.findByShortCode(code)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void anOgFetchRecordsItsResultWithoutChangingTheLinkVersion(boolean scraped) {
    when(scraper.fetch(anyString()))
        .thenReturn(scraped ? new OgMetadata("Title", "Desc", null) : OgMetadata.empty());
    ShortCode code = link();
    LinkEntity before = links.findByShortCode(code).orElseThrow();

    ogFetch.fetchAfterCommit(code, URL);

    LinkEntity after = links.findByShortCode(code).orElseThrow();
    assertThat(after.getVersion()).isEqualTo(before.getVersion());
    assertThat(after.getOgFetchAttempts()).isEqualTo(before.getOgFetchAttempts() + 1);
    assertThat(after.getOgTitle()).isEqualTo(scraped ? "Title" : null);
    assertThat(after.getOgFetchStatus()).isEqualTo(scraped ? "OK" : "RETRYABLE");
    links.delete(after);
  }

  private ShortCode link() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserEntity owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    return links.save(new LinkEntity(URL, "o" + suffix, owner.getId(), null)).getShortCode();
  }
}
