package com.example.short_link.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationJourneyHttpQueryContractTest extends AccountHttpJourneySupport {
  @Autowired private NotificationRepository notifications;
  @Autowired private LinkNotificationRepository linkNotifications;

  @Test
  void likeNotificationKeepsItsPostButHidesTheActorAfterAccountDeletion() throws Exception {
    String avatarUrl = "https://example.com/actor-" + stranger.getId() + ".png";
    jdbc.update("UPDATE users SET avatar_url = ? WHERE id = ?", avatarUrl, stranger.getId());
    long postId =
        body(call(
                "notification-actor-post-create",
                "POST",
                "/api/v1/posts",
                Map.of(
                    "slug",
                    "actor-retention-" + owner.getId(),
                    "title",
                    "A notification outlives its actor",
                    "languageTag",
                    "ko"),
                token,
                201))
            .path("id")
            .asLong();
    call(
        "notification-actor-post-publish",
        "POST",
        "/api/v1/posts/" + postId + "/publish",
        null,
        token,
        200);
    call(
        "notification-actor-like",
        "PUT",
        "/api/v1/posts/" + postId + "/like",
        null,
        strangerToken,
        200);
    assertThat(
            count(
                "SELECT COUNT(*) FROM post_like WHERE post_id = ? AND user_id = ?",
                postId,
                stranger.getId()))
        .isEqualTo(1);
    long notificationId =
        count(
            "SELECT id FROM notification WHERE recipient_user_id = ? AND actor_user_id = ? AND type = 'LIKE'",
            owner.getId(),
            stranger.getId());
    var before =
        body(call("notification-actor-visible", "GET", "/api/v1/notifications", null, token, 200))
            .path("items");
    assertThat(before.size()).isEqualTo(1);
    assertThat(before.get(0).path("actorId").asLong()).isEqualTo(stranger.getId());
    assertThat(before.get(0).path("actorUsername").asText()).isEqualTo(stranger.getUsername());
    assertThat(before.get(0).path("actorAvatarUrl").asText()).isEqualTo(avatarUrl);

    call(
        "notification-actor-account-delete",
        "DELETE",
        "/api/v1/users/me",
        null,
        strangerToken,
        204);
    assertThat(
            count(
                "SELECT COUNT(*) FROM users WHERE id = ? AND deleted_at IS NOT NULL",
                stranger.getId()))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE id = ? AND actor_user_id = ?",
                notificationId,
                stranger.getId()))
        .isEqualTo(1);
    var after =
        body(call("notification-actor-anonymous", "GET", "/api/v1/notifications", null, token, 200))
            .path("items");
    assertThat(after.size()).isEqualTo(1);
    var retained = after.get(0);
    assertThat(retained.path("id").asLong()).isEqualTo(notificationId);
    assertThat(retained.path("postId").asLong()).isEqualTo(postId);
    assertThat(retained.path("type").asText()).isEqualTo("LIKE");
    assertThat(retained.path("actorId").isNull()).isTrue();
    assertThat(retained.path("actorUsername").isNull()).isTrue();
    assertThat(retained.path("actorAvatarUrl").isNull()).isTrue();
    assertThat(retained.path("read").asBoolean()).isFalse();
  }

  @Test
  void recipientReadsOwnBlogInboxWithoutMarkingSomeoneElsesNotification() throws Exception {
    long[] ids =
        transactions.execute(
            status ->
                new long[] {
                  notifications
                      .save(
                          new NotificationEntity(
                              owner.getId(), NotificationType.FOLLOW, stranger.getId(), null))
                      .getId(),
                  notifications
                      .save(
                          new NotificationEntity(
                              owner.getId(), NotificationType.FOLLOW, stranger.getId(), null))
                      .getId(),
                  notifications
                      .save(
                          new NotificationEntity(
                              stranger.getId(), NotificationType.FOLLOW, owner.getId(), null))
                      .getId()
                });
    String path = "/api/v1/notifications";
    var page = body(call("notification-blog-list", "GET", path, null, token, 200));
    assertThat(page.path("items").size()).isEqualTo(2);
    assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(ids[1]);
    assertThat(
            body(call("notification-blog-unread", "GET", path + "/unread-count", null, token, 200))
                .path("count")
                .asLong())
        .isEqualTo(2);
    call(
        "notification-blog-cross-user-read",
        "POST",
        path + "/" + ids[2] + "/read",
        null,
        token,
        204);
    assertThat(count("select count(*) from notification where id=? and read_at is null", ids[2]))
        .isEqualTo(1);
    call("notification-blog-read", "POST", path + "/" + ids[0] + "/read", null, token, 204);
    assertThat(
            count("select count(*) from notification where id=? and read_at is not null", ids[0]))
        .isEqualTo(1);
    call("notification-blog-read-all", "POST", path + "/read-all", null, token, 200);
    assertThat(
            count(
                "select count(*) from notification where recipient_user_id=? and read_at is null",
                owner.getId()))
        .isZero();
    assertThat(count("select count(*) from notification where id=? and read_at is null", ids[2]))
        .isEqualTo(1);
  }

  @Test
  void recipientReadsOwnLinkInboxWithoutChangingAnotherRecipient() throws Exception {
    long[] ids =
        transactions.execute(
            status ->
                new long[] {
                  linkNotifications
                      .save(
                          new LinkNotificationEntity(
                              owner.getId(),
                              LinkNotificationType.FIRST_CLICK,
                              "hello01",
                              "first",
                              "one"))
                      .getId(),
                  linkNotifications
                      .save(
                          new LinkNotificationEntity(
                              owner.getId(),
                              LinkNotificationType.MILESTONE,
                              "hello01",
                              "milestone",
                              "hundred"))
                      .getId(),
                  linkNotifications
                      .save(
                          new LinkNotificationEntity(
                              stranger.getId(),
                              LinkNotificationType.FIRST_CLICK,
                              "other01",
                              "other",
                              "private"))
                      .getId()
                });
    String path = "/api/v1/links/notifications";
    var page = body(call("notification-link-list", "GET", path, null, token, 200));
    assertThat(page.path("items").size()).isEqualTo(2);
    assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(ids[1]);
    assertThat(
            body(call("notification-link-unread", "GET", path + "/unread-count", null, token, 200))
                .path("count")
                .asLong())
        .isEqualTo(2);
    call(
        "notification-link-cross-user-read",
        "POST",
        path + "/" + ids[2] + "/read",
        null,
        token,
        204);
    assertThat(
            count("select count(*) from link_notification where id=? and read_at is null", ids[2]))
        .isEqualTo(1);
    call("notification-link-read", "POST", path + "/" + ids[0] + "/read", null, token, 204);
    assertThat(
            count(
                "select count(*) from link_notification where id=? and read_at is not null",
                ids[0]))
        .isEqualTo(1);
    call("notification-link-read-all", "POST", path + "/read-all", null, token, 200);
    assertThat(
            count(
                "select count(*) from link_notification where recipient_user_id=? and read_at is null",
                owner.getId()))
        .isZero();
    assertThat(
            count("select count(*) from link_notification where id=? and read_at is null", ids[2]))
        .isEqualTo(1);
  }

  @Test
  void userChangesEachKindOfPreferenceAndAnotherUserKeepsDefaults() throws Exception {
    call(
        "notification-blog-preference-set",
        "PUT",
        "/api/v1/notifications/blog-preferences",
        Map.of("type", "FOLLOW", "enabled", false),
        token,
        204);
    assertThat(
            count(
                "select count(*) from blog_notification_preference where user_id=? and type='FOLLOW' and enabled=false",
                owner.getId()))
        .isEqualTo(1);
    assertThat(
            body(call(
                    "notification-blog-preference-list",
                    "GET",
                    "/api/v1/notifications/blog-preferences",
                    null,
                    token,
                    200))
                .path("FOLLOW")
                .asBoolean())
        .isFalse();
    assertThat(
            body(call(
                    "notification-blog-preference-other-user",
                    "GET",
                    "/api/v1/notifications/blog-preferences",
                    null,
                    strangerToken,
                    200))
                .path("FOLLOW")
                .asBoolean())
        .isTrue();
    call(
        "notification-link-preference-set",
        "PUT",
        "/api/v1/notifications/preferences",
        Map.of("type", "FIRST_CLICK", "enabled", false),
        token,
        204);
    assertThat(
            count(
                "select count(*) from notification_preference where user_id=? and type='FIRST_CLICK' and enabled=false",
                owner.getId()))
        .isEqualTo(1);
    var preferences =
        body(
            call(
                "notification-link-preference-list",
                "GET",
                "/api/v1/notifications/preferences",
                null,
                token,
                200));
    assertThat(preferences.path("FIRST_CLICK").asBoolean()).isFalse();
    assertThat(preferences.has("WARNING")).isFalse();
  }

  @Test
  void registersUpdatesAndRemovesDeviceAndBrowserSubscriptions() throws Exception {
    String deviceToken = "device-" + owner.getId();
    Map<String, Object> device = Map.of("token", deviceToken, "platform", "ios");
    String devices = "/api/v1/notifications/devices";
    call("notification-device-register", "POST", devices, device, token, 204);
    call("notification-device-register-again", "POST", devices, device, token, 204);
    assertThat(
            count(
                "select count(*) from device_token where user_id=? and token=?",
                owner.getId(),
                deviceToken))
        .isEqualTo(1);
    call("notification-device-cross-user-remove", "DELETE", devices, device, strangerToken, 204);
    assertThat(
            count(
                "select count(*) from device_token where user_id=? and token=?",
                owner.getId(),
                deviceToken))
        .isEqualTo(1);
    call("notification-device-remove", "DELETE", devices, device, token, 204);
    assertThat(
            count(
                "select count(*) from device_token where user_id=? and token=?",
                owner.getId(),
                deviceToken))
        .isZero();

    String endpoint = "https://fcm.googleapis.com/fcm/send/http-" + owner.getId();
    String pushes = "/api/v1/notifications/web-push";
    call(
        "notification-webpush-subscribe",
        "POST",
        pushes,
        Map.of("endpoint", endpoint, "p256dh", "public-key", "auth", "secret"),
        token,
        204);
    assertThat(
            count(
                "select count(*) from web_push_subscription where user_id=? and endpoint=?",
                owner.getId(),
                endpoint))
        .isEqualTo(1);
    String remove = pushes + "?endpoint=" + URLEncoder.encode(endpoint, StandardCharsets.UTF_8);
    call("notification-webpush-cross-user-remove", "DELETE", remove, null, strangerToken, 204);
    assertThat(
            count(
                "select count(*) from web_push_subscription where user_id=? and endpoint=?",
                owner.getId(),
                endpoint))
        .isEqualTo(1);
    call("notification-webpush-unsubscribe", "DELETE", remove, null, token, 204);
    assertThat(
            count(
                "select count(*) from web_push_subscription where user_id=? and endpoint=?",
                owner.getId(),
                endpoint))
        .isZero();
  }
}
