package com.example.short_link.notification.application.preference;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.application.link.NotificationPreferenceService;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class NotificationPreferenceConcurrencyIntegrationTest {
  @Autowired private BlogNotificationPreferenceService blogPreferences;
  @Autowired private NotificationPreferenceService linkPreferences;
  @Autowired private UserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private TransactionTemplate transactions;
  private Long userId;

  @BeforeEach
  void createUser() {
    transactions = new TransactionTemplate(transactionManager);
    String unique = UUID.randomUUID().toString();
    userId =
        transactions.execute(
            status ->
                users
                    .save(new UserEntity("preference-" + unique + "@x.com", "google", unique))
                    .getId());
  }

  @AfterEach
  void cleanup() {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update("delete from blog_notification_preference where user_id = ?", userId);
          jdbc.update("delete from notification_preference where user_id = ?", userId);
          users.deleteById(userId);
        });
  }

  @ParameterizedTest
  @EnumSource(Preference.class)
  void simultaneousFirstUpdatesCommitOneRowAndLaterUpdatesKeepItsIdentity(Preference preference)
      throws Exception {
    CountDownLatch absentReads = new CountDownLatch(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> setAfterBothObservedAbsent(preference, absentReads));
      var second = executor.submit(() -> setAfterBothObservedAbsent(preference, absentReads));
      first.get(20, TimeUnit.SECONDS);
      second.get(20, TimeUnit.SECONDS);
    }

    assertThat(isEnabled(preference)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + preference.table + " where user_id = ?",
                Long.class,
                userId))
        .isEqualTo(1L);
    var original =
        jdbc.queryForMap(
            "select id, created_at from " + preference.table + " where user_id = ?", userId);

    setEnabled(preference, true);

    assertThat(isEnabled(preference)).isTrue();
    assertThat(
            jdbc.queryForMap(
                "select id, created_at from " + preference.table + " where user_id = ?", userId))
        .isEqualTo(original);
  }

  @ParameterizedTest
  @EnumSource(Preference.class)
  void preferenceWriteStillParticipatesInTheCallingTransaction(Preference preference) {
    transactions.executeWithoutResult(
        status -> {
          setEnabled(preference, false);
          assertThat(isEnabled(preference)).isFalse();
          status.setRollbackOnly();
        });

    assertThat(isEnabled(preference)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + preference.table + " where user_id = ?",
                Long.class,
                userId))
        .isZero();
  }

  private void setAfterBothObservedAbsent(Preference preference, CountDownLatch absentReads) {
    transactions.executeWithoutResult(
        status -> {
          // Establish both repeatable-read snapshots before either caller writes the absent key.
          assertThat(isEnabled(preference)).isTrue();
          absentReads.countDown();
          try {
            assertThat(absentReads.await(10, TimeUnit.SECONDS)).isTrue();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          setEnabled(preference, false);
        });
  }

  private boolean isEnabled(Preference preference) {
    return preference == Preference.BLOG
        ? blogPreferences.isEnabled(userId, NotificationType.LIKE)
        : linkPreferences.isEnabled(userId, LinkNotificationType.FIRST_CLICK);
  }

  private void setEnabled(Preference preference, boolean enabled) {
    if (preference == Preference.BLOG)
      blogPreferences.setEnabled(userId, NotificationType.LIKE, enabled);
    else linkPreferences.setEnabled(userId, LinkNotificationType.FIRST_CLICK, enabled);
  }

  enum Preference {
    BLOG("blog_notification_preference"),
    LINK("notification_preference");

    private final String table;

    Preference(String table) {
      this.table = table;
    }
  }
}
