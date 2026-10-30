package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Not @Transactional: the test asks whether a transaction is open while the page is fetched.
@SpringBootTest
@ActiveProfiles("test")
class OgRefreshOutsideTransactionIntegrationTest {

  private static final String PAGE =
      "<html><head><meta property=\"og:title\" content=\"새 제목\"></head></html>";

  @MockitoBean private HttpFetcher httpFetcher;
  @Autowired private LinkOgFetchService og;
  @Autowired private LinkRepository links;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> fetched = new CopyOnWriteArrayList<>();
  private UserEntity owner;
  private LinkEntity link;

  @BeforeEach
  void aLinkWhosePageIsDueARefresh() {
    when(httpFetcher.fetch(any()))
        .thenAnswer(
            call -> {
              fetched.add(where());
              return new HttpFetcher.Response(
                  200,
                  Map.of("Content-Type", List.of("text/html; charset=utf-8")),
                  PAGE.getBytes(StandardCharsets.UTF_8));
            });
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(new LinkEntity("https://1.1.1.1/" + suffix, "o" + suffix, owner.getId(), null));
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
  }

  @Test
  void aScheduledRefreshFetchesThePageOutsideATransaction() {
    og.refresh(link.getShortCode(), link.getOriginalUrl());

    assertThat(fetched).containsExactly("outside");
  }
}
