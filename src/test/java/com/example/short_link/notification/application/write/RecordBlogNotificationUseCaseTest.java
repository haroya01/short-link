package com.example.short_link.notification.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RecordBlogNotificationUseCaseTest {

  @Mock private NotificationRepository repository;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private PushSender pushSender;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private UserRepository userRepository;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private BlogNotificationPreferenceService preferenceService;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final MessageSource messageSource = pushMessages();

  /** 실제 messages_*.properties 를 로드해 로컬라이즈를 실측한다. */
  private static MessageSource pushMessages() {
    ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    return ms;
  }

  private static UserEntity userWith(long id, String locale) {
    UserEntity u = org.mockito.Mockito.mock(UserEntity.class);
    when(u.getId()).thenReturn(id);
    when(u.getLocale()).thenReturn(locale);
    return u;
  }

  /** Default: every type enabled for every recipient — existing behavior is preference-free. */
  @org.junit.jupiter.api.BeforeEach
  void defaultsToEveryTypeEnabled() {
    when(preferenceService.isEnabled(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(NotificationType.class)))
        .thenReturn(true);
    when(preferenceService.filterEnabled(
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.any(NotificationType.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private RecordBlogNotificationUseCase useCase() {
    return new RecordBlogNotificationUseCase(
        repository, jsonMapper, pushSender, userRepository, messageSource, preferenceService);
  }

  @Test
  void serializesPostReferenceIntoPayload() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase()
        .record(9L, NotificationType.LIKE, 2L, new NotificationPostRef(10L, "my-post", "Hi", null));

    ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
    org.mockito.Mockito.verify(repository).save(saved.capture());
    NotificationEntity e = saved.getValue();
    assertThat(e.getRecipientUserId()).isEqualTo(9L);
    assertThat(e.getType()).isEqualTo(NotificationType.LIKE);
    assertThat(e.getActorUserId()).isEqualTo(2L);
    assertThat(e.getPayload()).contains("\"slug\":\"my-post\"").contains("\"title\":\"Hi\"");
  }

  @Test
  void leavesPayloadNullWhenNoPost() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase().record(9L, NotificationType.FOLLOW, 2L, null);

    ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
    org.mockito.Mockito.verify(repository).save(saved.capture());
    assertThat(saved.getValue().getPayload()).isNull();
  }

  @Test
  void pushMirrorsEveryTypeWithActorName() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    UserEntity actor = org.mockito.Mockito.mock(UserEntity.class);
    when(actor.getUsername()).thenReturn("yuki");
    when(userRepository.findById(2L)).thenReturn(Optional.of(actor));

    Map<NotificationType, String> expected =
        Map.ofEntries(
            Map.entry(NotificationType.LIKE, "yuki님이 글을 좋아합니다"),
            Map.entry(NotificationType.COMMENT, "yuki님이 댓글을 남겼습니다"),
            Map.entry(NotificationType.REPLY, "yuki님이 답글을 남겼습니다"),
            Map.entry(NotificationType.FOLLOW, "yuki님이 팔로우하기 시작했습니다"),
            Map.entry(NotificationType.SERIES_SUBSCRIBE, "yuki님이 시리즈를 구독합니다"),
            Map.entry(NotificationType.NEW_POST, "yuki님이 새 글을 발행했습니다"),
            Map.entry(NotificationType.MENTION, "yuki님이 회원님을 언급했습니다"),
            Map.entry(NotificationType.CONNECTED, "yuki님이 회원님의 글을 컬렉션에 엮었습니다"),
            Map.entry(NotificationType.PATH_GREW, "yuki님이 회원님이 속한 컬렉션에 새로 엮었습니다"));

    NotificationPostRef ref = new NotificationPostRef(10L, "my-post", "글 제목", null);
    for (NotificationType type : NotificationType.values()) {
      useCase().record(9L, type, 2L, ref);
    }

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender, org.mockito.Mockito.times(expected.size()))
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getAllValues())
        .allSatisfy(
            message -> {
              assertThat(message.title()).isEqualTo("kurl");
              assertThat(message.subtitle()).isEqualTo("글 제목");
            });
    assertThat(pushed.getAllValues())
        .extracting(PushSender.PushMessage::body)
        .containsExactlyInAnyOrderElementsOf(expected.values());
  }

  @Test
  void pushIsLocalizedToRecipientLocale() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    UserEntity actor = org.mockito.Mockito.mock(UserEntity.class);
    when(actor.getUsername()).thenReturn("yuki");
    when(userRepository.findById(2L)).thenReturn(Optional.of(actor));
    UserEntity recipient = org.mockito.Mockito.mock(UserEntity.class);
    when(recipient.getLocale()).thenReturn("ja");
    when(userRepository.findById(9L)).thenReturn(Optional.of(recipient));

    useCase().record(9L, NotificationType.LIKE, 2L, null);

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getValue().body()).isEqualTo("yukiさんが投稿にいいねしました");
  }

  @Test
  void pushFallsBackToKurlWhenActorMissingOrNameless() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());

    useCase().record(9L, NotificationType.FOLLOW, 2L, null);

    UserEntity nameless = org.mockito.Mockito.mock(UserEntity.class);
    when(nameless.getUsername()).thenReturn(null);
    when(userRepository.findById(3L)).thenReturn(Optional.of(nameless));

    useCase().record(9L, NotificationType.FOLLOW, 3L, null);

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender, org.mockito.Mockito.times(2))
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getAllValues())
        .extracting(PushSender.PushMessage::body)
        .containsOnly("kurl님이 팔로우하기 시작했습니다");
    assertThat(pushed.getAllValues().getFirst().subtitle()).isNull();
  }

  @Test
  void fanOutSavesPerRecipientAndPushesOnce() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    UserEntity r7 = userWith(7L, "ko");
    UserEntity r8 = userWith(8L, "ko");
    UserEntity r9 = userWith(9L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7, r8, r9));

    NotificationPostRef ref = new NotificationPostRef(10L, "new-post", "새 글", null);
    useCase().recordForEach(List.of(7L, 8L, 9L), NotificationType.NEW_POST, 2L, ref);

    org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(3))
        .save(org.mockito.ArgumentMatchers.any(NotificationEntity.class));
    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .sendToAll(org.mockito.ArgumentMatchers.eq(List.of(7L, 8L, 9L)), pushed.capture());
    assertThat(pushed.getValue().subtitle()).isEqualTo("새 글");
    assertThat(pushed.getValue().body()).isEqualTo("kurl님이 새 글을 발행했습니다");
  }

  @Test
  void fanOutWithNoRecipientsIsSilent() {
    useCase().recordForEach(List.of(), NotificationType.NEW_POST, 2L, null);

    org.mockito.Mockito.verifyNoInteractions(repository, pushSender);
  }

  @Test
  void fanOutWithoutPostRefAndTitlelessRefHaveNoSubtitle() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    UserEntity r7 = userWith(7L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7));

    useCase().recordForEach(List.of(7L), NotificationType.NEW_POST, 2L, null);
    useCase().record(9L, NotificationType.LIKE, 2L, new NotificationPostRef(10L, "p", null, null));

    ArgumentCaptor<PushSender.PushMessage> fanned =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .sendToAll(org.mockito.ArgumentMatchers.eq(List.of(7L)), fanned.capture());
    assertThat(fanned.getValue().subtitle()).isNull();

    ArgumentCaptor<PushSender.PushMessage> single =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .send(org.mockito.ArgumentMatchers.eq(9L), single.capture());
    assertThat(single.getValue().subtitle()).isNull();
  }

  @Test
  void mutedRecipientGetsNoBellRowAndNoPush() {
    when(preferenceService.isEnabled(9L, NotificationType.LIKE)).thenReturn(false);

    useCase().record(9L, NotificationType.LIKE, 2L, new NotificationPostRef(10L, "p", "t", null));

    org.mockito.Mockito.verifyNoInteractions(repository, pushSender);
  }

  @Test
  void mutingOneTypeLeavesOthersDelivered() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    when(preferenceService.isEnabled(9L, NotificationType.FOLLOW)).thenReturn(false);

    RecordBlogNotificationUseCase useCase = useCase();
    useCase.record(9L, NotificationType.FOLLOW, 2L, null);
    useCase.record(9L, NotificationType.COMMENT, 2L, null);

    ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
    org.mockito.Mockito.verify(repository).save(saved.capture());
    assertThat(saved.getValue().getType()).isEqualTo(NotificationType.COMMENT);
    org.mockito.Mockito.verify(pushSender, org.mockito.Mockito.times(1))
        .send(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void fanOutSkipsFollowersWhoMutedNewPost() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    UserEntity r7 = userWith(7L, "ko");
    UserEntity r9 = userWith(9L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7, r9));
    // 8 muted NEW_POST; the filtered list keeps 7 and 9 in order.
    when(preferenceService.filterEnabled(List.of(7L, 8L, 9L), NotificationType.NEW_POST))
        .thenReturn(List.of(7L, 9L));

    NotificationPostRef ref = new NotificationPostRef(10L, "new-post", "새 글", null);
    useCase().recordForEach(List.of(7L, 8L, 9L), NotificationType.NEW_POST, 2L, ref);

    org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2))
        .save(org.mockito.ArgumentMatchers.any(NotificationEntity.class));
    org.mockito.Mockito.verify(pushSender)
        .sendToAll(
            org.mockito.ArgumentMatchers.eq(List.of(7L, 9L)),
            org.mockito.ArgumentMatchers.any(PushSender.PushMessage.class));
  }

  @Test
  void fanOutIsSilentWhenEveryFollowerMutedNewPost() {
    when(preferenceService.filterEnabled(List.of(7L, 8L), NotificationType.NEW_POST))
        .thenReturn(List.of());

    useCase().recordForEach(List.of(7L, 8L), NotificationType.NEW_POST, 2L, null);

    org.mockito.Mockito.verifyNoInteractions(repository, pushSender);
  }

  @Test
  void connectedSerializesCollectionRefAndSubtitlesWithCollectionName() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    UserEntity actor = org.mockito.Mockito.mock(UserEntity.class);
    when(actor.getUsername()).thenReturn("yuki");
    when(userRepository.findById(2L)).thenReturn(Optional.of(actor));

    useCase()
        .record(
            9L,
            NotificationType.CONNECTED,
            2L,
            new NotificationCollectionRef(42L, "긴 여름의 독서", 10L));

    ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
    org.mockito.Mockito.verify(repository).save(saved.capture());
    assertThat(saved.getValue().getType()).isEqualTo(NotificationType.CONNECTED);
    assertThat(saved.getValue().getPayload())
        .contains("\"collectionId\":42")
        .contains("\"collectionName\":\"긴 여름의 독서\"")
        .contains("\"postId\":10");

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getValue().subtitle()).isEqualTo("긴 여름의 독서");
    assertThat(pushed.getValue().body()).isEqualTo("yuki님이 회원님의 글을 컬렉션에 엮었습니다");
  }

  @Test
  void pathGrewFanOutSerializesCollectionRefAndSubtitlesWithCollectionName() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    UserEntity r7 = userWith(7L, "ko");
    UserEntity r9 = userWith(9L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7, r9));

    useCase()
        .recordForEach(
            List.of(7L, 9L),
            NotificationType.PATH_GREW,
            2L,
            new NotificationCollectionRef(42L, "긴 여름의 독서", null));

    org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2))
        .save(org.mockito.ArgumentMatchers.any(NotificationEntity.class));
    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .sendToAll(org.mockito.ArgumentMatchers.eq(List.of(7L, 9L)), pushed.capture());
    assertThat(pushed.getValue().subtitle()).isEqualTo("긴 여름의 독서");
    assertThat(pushed.getValue().body()).isEqualTo("kurl님이 회원님이 속한 컬렉션에 새로 엮었습니다");
  }

  @Test
  void mutedRecipientGetsNoConnectedBellRowOrPush() {
    when(preferenceService.isEnabled(9L, NotificationType.CONNECTED)).thenReturn(false);

    useCase()
        .record(9L, NotificationType.CONNECTED, 2L, new NotificationCollectionRef(42L, "c", 10L));

    org.mockito.Mockito.verifyNoInteractions(repository, pushSender);
  }

  @Test
  void pathGrewFanOutSkipsContributorsWhoMutedIt() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    UserEntity r7 = userWith(7L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7));
    // 9 muted PATH_GREW; the filtered list keeps 7.
    when(preferenceService.filterEnabled(List.of(7L, 9L), NotificationType.PATH_GREW))
        .thenReturn(List.of(7L));

    useCase()
        .recordForEach(
            List.of(7L, 9L),
            NotificationType.PATH_GREW,
            2L,
            new NotificationCollectionRef(42L, "c", null));

    org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(1))
        .save(org.mockito.ArgumentMatchers.any(NotificationEntity.class));
    org.mockito.Mockito.verify(pushSender)
        .sendToAll(
            org.mockito.ArgumentMatchers.eq(List.of(7L)),
            org.mockito.ArgumentMatchers.any(PushSender.PushMessage.class));
  }
}
