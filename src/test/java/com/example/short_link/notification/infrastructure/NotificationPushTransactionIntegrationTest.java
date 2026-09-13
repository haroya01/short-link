package com.example.short_link.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class NotificationPushTransactionIntegrationTest {
  @Autowired private RecordBlogNotificationUseCase recordNotification;
  @Autowired private LinkNotificationDispatcher linkNotifications;
  @Autowired private NotificationPushDelivery pushDelivery;
  @Autowired private NotificationRepository notifications;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;

  // Replace the whole transport, so commit semantics cannot come from CompositePushSender.
  @MockitoBean(name = "compositePushSender")
  private PushSender transport;

  private TransactionTemplate transactions;
  private Long userId;

  @BeforeEach
  void createRecipient() {
    transactions = new TransactionTemplate(transactionManager);
    String unique = UUID.randomUUID().toString();
    userId =
        transactions.execute(
            status ->
                users.save(new UserEntity("push-" + unique + "@x.com", "google", unique)).getId());
  }

  @AfterEach
  void cleanup() {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update("delete from notification where recipient_user_id = ?", userId);
          jdbc.update("delete from link_notification where recipient_user_id = ?", userId);
          users.deleteById(userId);
        });
  }

  @Test
  void anyTransportSeesTheCommittedBellRowAndItsFailureLeavesItStored() {
    doAnswer(
            invocation -> {
              // A separate connection cannot see an uncommitted notification insert.
              try (var connection = dataSource.getConnection();
                  var statement =
                      connection.prepareStatement(
                          "select count(*) from notification where recipient_user_id = ?")) {
                statement.setLong(1, userId);
                try (var rows = statement.executeQuery()) {
                  assertThat(rows.next()).isTrue();
                  assertThat(rows.getLong(1)).isEqualTo(1L);
                }
              }
              throw new IllegalStateException("push submission failed");
            })
        .when(transport)
        .send(eq(userId), any());

    recordNotification.record(userId, NotificationType.FOLLOW, userId, null);

    assertThat(notifications.countUnread(userId)).isEqualTo(1L);
    verify(transport).send(eq(userId), any());
  }

  @Test
  void rollbackDiscardsTheLinkBellRowAndItsPush() {
    transactions.executeWithoutResult(
        status -> {
          linkNotifications.dispatch(userId, LinkNotificationType.WARNING, null, null, "Warning");
          verifyNoInteractions(transport);
          status.setRollbackOnly();
        });

    assertThat(
            jdbc.queryForObject(
                "select count(*) from link_notification where recipient_user_id = ?",
                Long.class,
                userId))
        .isZero();
    verifyNoInteractions(transport);
  }

  @Test
  void finalCommitFailureDoesNotSubmitPushWork() {
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      linkNotifications.dispatch(
                          userId, LinkNotificationType.WARNING, null, null, "Warning");
                      TransactionSynchronizationManager.registerSynchronization(
                          new TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                              throw new IllegalStateException("commit rejected");
                            }
                          });
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("commit rejected");

    assertThat(
            jdbc.queryForObject(
                "select count(*) from link_notification where recipient_user_id = ?",
                Long.class,
                userId))
        .isZero();
    verifyNoInteractions(transport);
  }

  @Test
  void fanoutSnapshotsRecipientsUntilCommitAndIsolatesAnyTransportFailure() {
    List<Long> recipients = new ArrayList<>(List.of(userId));
    PushSender.PushMessage message = new PushSender.PushMessage("kurl", null, "New post");
    doThrow(new IllegalStateException("channel unavailable"))
        .when(transport)
        .sendToAll(List.of(userId), message);

    transactions.executeWithoutResult(
        status -> {
          pushDelivery.sendToAll(recipients, message);
          recipients.clear();
          verifyNoInteractions(transport);
        });

    verify(transport).sendToAll(List.of(userId), message);
  }

  @Test
  void aSuccessfulLinkWriteCallsTheReplacementTransportOnlyAfterCommit() {
    transactions.executeWithoutResult(
        status -> {
          linkNotifications.dispatch(userId, LinkNotificationType.WARNING, null, null, "Warning");
          verifyNoInteractions(transport);
        });

    verify(transport).send(eq(userId), any());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from link_notification where recipient_user_id = ?",
                Long.class,
                userId))
        .isEqualTo(1L);
  }
}
