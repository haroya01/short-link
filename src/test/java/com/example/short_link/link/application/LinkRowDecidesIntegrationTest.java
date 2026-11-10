package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkRepository.ProfileLinkRow;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class LinkRowDecidesIntegrationTest {

  private static final String HASH = "$2a$10$" + "s".repeat(53);

  @Autowired private LinkRepository links;
  @Autowired private LinkLookupQueryService lookup;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CacheManager caches;
  @Autowired private PlatformTransactionManager transactions;

  private UserEntity owner;
  private LinkEntity link;

  @BeforeEach
  void anOwnedLinkWithNoRestrictions() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "d" + suffix, owner.getId(), null));
    forgetCachedVisit();
  }

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
    forgetCachedVisit();
  }

  @Test
  void aPasswordOnlyTheSidecarHoldsDoesNotStopTheVisitor() {
    sidecarAccess(HASH, null);

    assertThat(visit().passwordRequired()).isFalse();
  }

  @Test
  void aViewLimitOnlyTheSidecarHoldsIsNotApplied() {
    sidecarAccess(null, 1);

    assertThat(visit().maxViews()).isNull();
  }

  @Test
  void countriesOnlyTheSidecarBlocksStayOpen() {
    sidecarPolicy("KR", null);

    assertThat(visit().blockedCountries()).isNull();
  }

  @Test
  void aLimitMessageOnlyTheSidecarHoldsIsNotShown() {
    sidecarPolicy(null, "끝났어요");

    assertThat(visit().expiredMessage()).isNull();
  }

  @Test
  void theProfileLocksOnlyWhatTheLinkRowLocks() {
    jdbc.update("UPDATE link SET profile_order = 0 WHERE id = ?", link.getId());
    sidecarAccess(HASH, null);

    assertThat(links.findPublicProfileLinks(owner.getId()))
        .singleElement()
        .extracting(ProfileLinkRow::getPasswordRequired)
        .isEqualTo(false);
  }

  @Test
  void aPasswordOnlyTheSidecarHoldsDoesNotBlockReuse() {
    sidecarAccess(HASH, null);

    assertThat(links.findUnrestrictedByUserIdAndOriginalUrl(owner.getId(), link.getOriginalUrl()))
        .extracting(LinkEntity::getId)
        .containsExactly(link.getId());
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

  private CachedLink visit() {
    return lookup.findActiveLink(link.getShortCode());
  }

  private void forgetCachedVisit() {
    Objects.requireNonNull(caches.getCache("link")).evict(link.getShortCode());
  }

  private void sidecarAccess(String passwordHash, Integer maxViews) {
    jdbc.update(
        "INSERT INTO link_access_control (link_id, password_hash, max_views, created_at,"
            + " updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
        link.getId(),
        passwordHash,
        maxViews);
  }

  private void sidecarPolicy(String blockedCountries, String expiredMessage) {
    jdbc.update(
        "INSERT INTO link_expiration_policy (link_id, blocked_countries, expired_message,"
            + " created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6))",
        link.getId(),
        blockedCountries,
        expiredMessage);
  }
}
