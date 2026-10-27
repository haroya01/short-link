package com.example.short_link.notification.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.example.short_link.common.user.BlockEdge;
import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.SilenceCheck;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.write.NotificationFanoutWriter;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.policy.NotificationPolicy;
import com.example.short_link.notification.domain.policy.NotificationSender;
import com.example.short_link.notification.domain.repository.BlogNotificationPreferenceRepository;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationPolicyRepository;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.json.JsonMapper;

final class StoredNotices {

  record Row(
      Long recipient,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      NotificationTarget payload,
      String groupKey) {}

  record Push(List<Long> recipients, String type, PushRoute route) {}

  private static final Instant NOW = Instant.parse("2026-10-27T03:00:00Z");

  private final NotificationTargetCodec codec =
      new NotificationTargetCodec(JsonMapper.builder().build());
  private final List<NotificationEntity> saved = new ArrayList<>();
  private final List<Push> pushes = new ArrayList<>();
  private final Map<Long, NotificationUser> users = new HashMap<>();
  private final Set<List<Object>> silenced = new HashSet<>();
  private int userLookups;
  private int silenceChecks;

  final NotificationUserReader userReader =
      new NotificationUserReader() {
        @Override
        public Optional<NotificationUser> findById(Long userId) {
          return findAllByIdIn(List.of(userId)).stream().findFirst();
        }

        @Override
        public List<NotificationUser> findAllByIdIn(Collection<Long> userIds) {
          if (userIds.isEmpty()) {
            return List.of();
          }
          userLookups++;
          return userIds.stream().map(users::get).filter(Objects::nonNull).toList();
        }
      };

  private final UserBlockChecker blocks =
      new UserBlockChecker() {
        @Override
        public boolean isBlocked(BlockEdge edge) {
          throw new UnsupportedOperationException();
        }

        @Override
        public BlockRelation between(Long viewerId, Long otherId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean silences(SilenceCheck check) {
          Long recipientId = check.recipientId();
          if (recipientId == null || (check.actor().isNone() && check.conversationId() == null)) {
            return false;
          }
          silenceChecks++;
          return silenced.contains(
                  List.of(recipientId, "member", Objects.toString(check.actor().userId())))
              || silenced.contains(
                  List.of(recipientId, "remote", Objects.toString(check.actor().remoteId())))
              || silenced.contains(
                  List.of(recipientId, "conversation", Objects.toString(check.conversationId())));
        }
      };

  private final RecordBlogNotificationUseCase useCase;

  StoredNotices() {
    NotificationRepository repository =
        mock(NotificationRepository.class, withSettings().strictness(Strictness.LENIENT));
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              saved.add(invocation.getArgument(0));
              return invocation.getArgument(0);
            });
    when(repository.existsInGroup(any(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                saved.stream()
                    .anyMatch(
                        row ->
                            row.getRecipientUserId().equals(invocation.getArgument(0))
                                && Objects.equals(row.getGroupKey(), invocation.getArgument(1))
                                && Objects.equals(row.getActorUserId(), invocation.getArgument(2))
                                && Objects.equals(
                                    row.getActorRemoteId(), invocation.getArgument(3))));
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    useCase =
        new RecordBlogNotificationUseCase(
            repository,
            codec,
            new NotificationPushDelivery(new RecordingPushSender()),
            userReader,
            messages,
            new BlogNotificationPreferenceService(mock(BlogNotificationPreferenceRepository.class)),
            new NotificationFanoutWriter(repository),
            mock(NotificationActorReader.class),
            new AcceptingPolicies(),
            (userIds, now) -> Map.of(),
            blocks,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  RecordBlogNotificationUseCase useCase() {
    return useCase;
  }

  StoredNotices user(long id, String username) {
    users.put(id, new NotificationUser(id, username, "ko"));
    return this;
  }

  void silence(long recipient, long actor) {
    silenced.add(List.of(recipient, "member", Long.toString(actor)));
  }

  void silenceRemote(long recipient, long remoteActor) {
    silenced.add(List.of(recipient, "remote", Long.toString(remoteActor)));
  }

  void muteConversation(long recipient, long conversation) {
    silenced.add(List.of(recipient, "conversation", Long.toString(conversation)));
  }

  List<Row> rows() {
    return saved.stream()
        .map(
            row ->
                new Row(
                    row.getRecipientUserId(),
                    row.getType(),
                    row.getActorUserId(),
                    row.getActorRemoteId(),
                    codec.decode(row.getType(), row.getPayload()),
                    row.getGroupKey()))
        .toList();
  }

  List<Push> pushes() {
    return List.copyOf(pushes);
  }

  int userLookups() {
    return userLookups;
  }

  int silenceChecks() {
    return silenceChecks;
  }

  private final class RecordingPushSender implements PushSender {
    @Override
    public void send(Long recipientUserId, PushMessage message) {
      pushes.add(new Push(List.of(recipientUserId), message.type(), message.route()));
    }

    @Override
    public void sendToAll(Collection<Long> recipientUserIds, PushMessage message) {
      pushes.add(new Push(List.copyOf(recipientUserIds), message.type(), message.route()));
    }
  }

  private static final class AcceptingPolicies implements NotificationPolicyRepository {
    @Override
    public Optional<NotificationPolicy> find(Long userId) {
      return Optional.empty();
    }

    @Override
    public void save(Long userId, NotificationPolicy policy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public NotificationSender sender(
        Long recipientUserId,
        NotificationType type,
        Long actorUserId,
        Long actorRemoteId,
        Long mentionNoteId) {
      return new NotificationSender(true, null, false, true, true, false, false);
    }

    @Override
    public void permit(Long recipientUserId, Long actorUserId, Long actorRemoteId) {
      throw new UnsupportedOperationException();
    }
  }
}
