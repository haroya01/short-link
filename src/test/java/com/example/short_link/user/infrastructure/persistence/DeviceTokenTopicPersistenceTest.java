package com.example.short_link.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.user.application.write.DeviceTokenCommandService;
import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
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
              deviceTokens.deleteByUserId(userId);
              users.deleteById(userId);
            });
  }

  @Test
  void registeredTopicIsReadBackAndLegacyTokensLearnTheirTopic() {
    commands.register(userId, "links-token", "ios", "focustime.kurl.links", null);
    commands.register(userId, "legacy-token", "ios", null, null);

    assertThat(deviceTokens.targetsForUser(userId, Instant.now()))
        .containsExactlyInAnyOrder(
            new DeviceTarget("links-token", "focustime.kurl.links"),
            new DeviceTarget("legacy-token", null));

    deviceTokens.updateTopic("legacy-token", "focustime.kurl");

    assertThat(deviceTokens.targetsForUsers(List.of(userId), Instant.now()))
        .containsExactlyInAnyOrder(
            new DeviceTarget("links-token", "focustime.kurl.links"),
            new DeviceTarget("legacy-token", "focustime.kurl"));
  }

  @Test
  void reRegisteringWithoutTopicKeepsTheKnownOne() {
    commands.register(userId, "links-token", "ios", "focustime.kurl.links", null);
    commands.register(userId, "links-token", "ios", null, null);

    assertThat(deviceTokens.targetsForUser(userId, Instant.now()))
        .containsExactly(new DeviceTarget("links-token", "focustime.kurl.links"));
  }

  @Test
  void aDeviceHearsTheAccountOnlyWhileTheSessionThatRegisteredItLives() {
    commands.register(userId, "phone", "ios", null, "session-a");
    commands.register(userId, "tablet", "ios", null, "session-b");
    commands.register(userId, "old-install", "ios", null, null);

    assertThat(deviceTokens.targetsForUser(userId, Instant.now()))
        .extracting(DeviceTarget::token)
        .containsExactlyInAnyOrder("phone", "tablet", "old-install");

    deviceTokens.endSession(userId, "session-a");
    deviceTokens.extendSession(userId, "session-b", Instant.now().minusSeconds(60));

    assertThat(deviceTokens.targetsForUsers(List.of(userId), Instant.now()))
        .extracting(DeviceTarget::token)
        .containsExactly("old-install");

    deviceTokens.extendSession(userId, "session-b", Instant.now().plus(Duration.ofDays(14)));
    assertThat(deviceTokens.targetsForUser(userId, Instant.now()))
        .extracting(DeviceTarget::token)
        .containsExactlyInAnyOrder("tablet", "old-install");

    deviceTokens.deleteByUserId(userId);
    assertThat(deviceTokens.targetsForUser(userId, Instant.now())).isEmpty();
  }
}
