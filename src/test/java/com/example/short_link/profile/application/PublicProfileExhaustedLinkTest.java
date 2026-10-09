package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.write.IncrementViewCountCommand;
import com.example.short_link.link.application.write.IncrementViewCountUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.profile.application.read.ProfileQueryService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PublicProfileExhaustedLinkTest {
  @Autowired private ProfileQueryService profiles;
  @Autowired private IncrementViewCountUseCase incrementViewCount;
  @Autowired private CacheManager cacheManager;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
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
  void aLinkThatSpendsItsLastViewLeavesTheCachedProfileAtOnce() {
    UserEntity owner = profileOwner();
    LinkEntity once = profileLink(owner, 1, 1);
    LinkEntity twice = profileLink(owner, 2, 2);
    LinkEntity open = profileLink(owner, null, 3);
    assertThat(shownCodes(owner)).containsExactly(code(once), code(twice), code(open));
    awaitCached(owner);

    incrementViewCount.execute(new IncrementViewCountCommand(once.linkId()));
    incrementViewCount.execute(new IncrementViewCountCommand(twice.linkId()));

    assertThat(cacheManager.getCache(ProfileCacheEviction.CACHE_NAME).get(owner.getUsername()))
        .isNotNull();
    assertThat(shownCodes(owner)).containsExactly(code(twice), code(open));
  }

  private List<String> shownCodes(UserEntity owner) {
    return profiles.findByUsername(owner.getUsername()).entries().stream()
        .map(entry -> entry.shortCode().value())
        .toList();
  }

  private void awaitCached(UserEntity owner) {
    for (int i = 0;
        i < 500
            && cacheManager.getCache(ProfileCacheEviction.CACHE_NAME).get(owner.getUsername())
                == null;
        i++) {
      Thread.onSpinWait();
    }
  }

  private static String code(LinkEntity link) {
    return link.getShortCode().value();
  }

  private UserEntity profileOwner() {
    String tag = "px" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(tag + "@example.com", "google", tag);
    user.claimUsername(tag);
    UserEntity saved = users.save(user);
    createdUsers.add(saved.getId());
    return saved;
  }

  private LinkEntity profileLink(UserEntity owner, Integer maxViews, int order) {
    String code = "x" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    LinkEntity link = new LinkEntity("https://example.com/" + code, code, owner.getId(), null);
    link.setMaxViews(maxViews);
    link.setProfileOrder(order);
    return links.save(link);
  }
}
