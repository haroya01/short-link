package com.example.short_link.notification.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.application.dto.NotificationListResult;
import com.example.short_link.notification.application.dto.NotificationView;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationMentionsIntegrationTest {

  @Autowired private NotificationQueryService query;
  @Autowired private NotificationRepository notifications;
  @Autowired private UserRepository userRepository;

  private long reader;
  private final Map<NotificationType, Long> ids = new EnumMap<>(NotificationType.class);

  private long user() {
    String id = UUID.randomUUID().toString();
    UserEntity user = new UserEntity(id + "@example.com", "google", id);
    user.claimUsername("m" + id.replace("-", "").substring(0, 12));
    return userRepository.save(user).getId();
  }

  private long notice(long recipient, NotificationType type, long actor, boolean filtered) {
    return notifications
        .save(new NotificationEntity(recipient, type, actor, null, null, null, filtered))
        .getId();
  }

  @BeforeEach
  void oneNoticeOfEveryKind() {
    reader = user();
    long actor = user();
    for (NotificationType type : NotificationType.values()) {
      ids.put(type, notice(reader, type, actor, false));
    }
    notice(reader, NotificationType.MENTION, actor, true);
    notice(user(), NotificationType.MENTION, actor, false);
  }

  private static List<NotificationType> types(NotificationListResult result) {
    return result.items().stream().map(NotificationView::type).toList();
  }

  @Test
  void mentionsHoldMentionsRepliesAndCommentsNewestFirst() {
    NotificationListResult result = query.mentions(reader, null, 50);

    assertThat(types(result))
        .containsExactly(
            NotificationType.NOTE_MENTION,
            NotificationType.NOTE_REPLY,
            NotificationType.MENTION,
            NotificationType.REPLY,
            NotificationType.COMMENT);
    assertThat(result.hasMore()).isFalse();
    assertThat(query.list(reader, null, 50).items()).hasSize(NotificationType.values().length);
  }

  @Test
  void mentionsPageByTheirOwnCursor() {
    NotificationListResult first = query.mentions(reader, null, 2);
    NotificationListResult rest = query.mentions(reader, first.nextCursor(), 50);

    assertThat(types(first))
        .containsExactly(NotificationType.NOTE_MENTION, NotificationType.NOTE_REPLY);
    assertThat(first.hasMore()).isTrue();
    assertThat(first.nextCursor()).isEqualTo(ids.get(NotificationType.NOTE_REPLY));
    assertThat(types(rest))
        .containsExactly(
            NotificationType.MENTION, NotificationType.REPLY, NotificationType.COMMENT);
    assertThat(rest.hasMore()).isFalse();
  }
}
