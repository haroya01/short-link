package com.example.short_link.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.user.application.write.DeviceTokenCommandService;
import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DeviceTokenTopicPersistenceTest extends DockerHttpTest {

  @Autowired private DeviceTokenCommandService commands;
  @Autowired private DeviceTokenRepository deviceTokens;
  @Autowired private UserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;

  private Long userId;

  @BeforeEach
  void commitUser() {
    userId =
        new TransactionTemplate(transactionManager)
            .execute(
                transaction -> {
                  UserEntity user = new UserEntity("push@example.com", "google", "push-user");
                  user.claimUsername("push-user");
                  return users.save(user).getId();
                });
  }

  @AfterEach
  void removeFixtures() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            transaction -> {
              deviceTokens
                  .targetsForUser(userId)
                  .forEach(t -> deviceTokens.deleteByToken(t.token()));
              users.deleteById(userId);
            });
  }

  @Test
  void registeredTopicIsReadBackAndLegacyTokensLearnTheirTopic() {
    commands.register(userId, "links-token", "ios", "focustime.kurl.links");
    commands.register(userId, "legacy-token", "ios", null);

    assertThat(deviceTokens.targetsForUser(userId))
        .containsExactlyInAnyOrder(
            new DeviceTarget("links-token", "focustime.kurl.links"),
            new DeviceTarget("legacy-token", null));

    deviceTokens.updateTopic("legacy-token", "focustime.kurl");

    assertThat(deviceTokens.targetsForUsers(List.of(userId)))
        .containsExactlyInAnyOrder(
            new DeviceTarget("links-token", "focustime.kurl.links"),
            new DeviceTarget("legacy-token", "focustime.kurl"));
  }

  @Test
  void reRegisteringWithoutTopicKeepsTheKnownOne() {
    commands.register(userId, "links-token", "ios", "focustime.kurl.links");
    commands.register(userId, "links-token", "ios", null);

    assertThat(deviceTokens.targetsForUser(userId))
        .containsExactly(new DeviceTarget("links-token", "focustime.kurl.links"));
  }
}
