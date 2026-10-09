package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.application.write.DeleteLinkCommand;
import com.example.short_link.link.application.write.DeleteLinkUseCase;
import com.example.short_link.link.application.write.UpdateLinkCommand;
import com.example.short_link.link.application.write.UpdateLinkUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.moderation.application.LinkModerationService;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import com.example.short_link.link.visit.application.LinkVisitOptionService;
import com.example.short_link.profile.application.read.ProfileQueryService;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.application.write.UserDeletionService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PublicProfileCacheEvictionTest {
  @Autowired private ProfileQueryService profiles;
  @Autowired private CacheManager cacheManager;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private LinkProtectionService protection;
  @Autowired private LinkModerationService moderation;
  @Autowired private LinkVisitOptionService visitOptions;
  @Autowired private UpdateLinkUseCase updateLink;
  @Autowired private DeleteLinkUseCase deleteLink;
  @Autowired private UserDeletionService userDeletion;
  @Autowired private JdbcTemplate jdbc;

  private final List<Long> createdUsers = new ArrayList<>();

  @AfterEach
  void deleteCommittedRows() {
    for (Long userId : createdUsers) {
      jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  void protectingAProfileLinkDropsTheCachedProfile() {
    assertDropsTheProfile((owner, code) -> protection.update(owner.getId(), code, "secret", null));
  }

  @Test
  void disablingAProfileLinkDropsTheCachedProfile() {
    assertDropsTheProfile(
        (owner, code) -> moderation.disable(code, LinkDisableReason.SAFE_BROWSING, null));
  }

  @Test
  void schedulingAProfileLinkDropsTheCachedProfile() {
    assertDropsTheProfile(
        (owner, code) ->
            visitOptions.update(
                owner.getId(), code, null, null, Instant.now().plus(1, ChronoUnit.DAYS), false));
  }

  @Test
  void editingAProfileLinkDropsTheCachedProfile() {
    assertDropsTheProfile(
        (owner, code) ->
            updateLink.execute(
                new UpdateLinkCommand(
                    owner.getId(),
                    code,
                    null,
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    null,
                    null,
                    false)));
  }

  @Test
  void deletingAProfileLinkDropsTheCachedProfile() {
    assertDropsTheProfile(
        (owner, code) -> deleteLink.execute(new DeleteLinkCommand(owner.getId(), code)));
  }

  @Test
  void aLinkThatIsNotOnTheProfileLeavesTheCachedProfileAlone() {
    UserEntity owner = profileOwner();
    ShortCode hidden = link(owner, false);
    profiles.findByUsername(owner.getUsername());
    awaitCached(owner);

    protection.update(owner.getId(), hidden, "secret", null);

    assertThat(cachedProfile(owner)).isNotNull();
  }

  @Test
  void deletingTheAccountDropsTheCachedProfileAndItIsNotFoundAfterwards() {
    UserEntity owner = profileOwner();
    link(owner, true);
    profiles.findByUsername(owner.getUsername());
    awaitCached(owner);

    userDeletion.deleteAccount(owner.getId());

    assertThat(cachedProfile(owner)).isNull();
    assertThatThrownBy(() -> profiles.findByUsername(owner.getUsername()))
        .isInstanceOf(ProfileException.class);
  }

  private void assertDropsTheProfile(BiConsumer<UserEntity, ShortCode> change) {
    UserEntity owner = profileOwner();
    ShortCode shown = link(owner, true);
    profiles.findByUsername(owner.getUsername());
    awaitCached(owner);

    change.accept(owner, shown);

    assertThat(cachedProfile(owner)).isNull();
  }

  private void awaitCached(UserEntity owner) {
    for (int i = 0; i < 500 && cachedProfile(owner) == null; i++) {
      Thread.onSpinWait();
    }
    assertThat(cachedProfile(owner)).isNotNull();
  }

  private Cache.ValueWrapper cachedProfile(UserEntity owner) {
    return cacheManager.getCache(ProfileCacheEviction.CACHE_NAME).get(owner.getUsername());
  }

  private UserEntity profileOwner() {
    String tag = "pc" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(tag + "@example.com", "google", tag);
    user.claimUsername(tag);
    UserEntity saved = users.save(user);
    createdUsers.add(saved.getId());
    return saved;
  }

  private ShortCode link(UserEntity owner, boolean onProfile) {
    String code = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    LinkEntity link = new LinkEntity("https://example.com/" + code, code, owner.getId(), null);
    if (onProfile) link.setProfileOrder(0);
    links.save(link);
    return new ShortCode(code);
  }
}
