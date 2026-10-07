package com.example.short_link.notification.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushApp;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
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
  private NotificationUserReader userRepository;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private BlogNotificationPreferenceService preferenceService;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private NotificationFanoutWriter fanoutWriter;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private NotificationActorReader actorReader;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final MessageSource messageSource = pushMessages();

  private static MessageSource pushMessages() {
    ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    return ms;
  }

  private static NotificationUser userWith(long id, String locale) {
    return new NotificationUser(id, null, locale);
  }

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
        repository,
        new NotificationTargetCodec(jsonMapper),
        new NotificationPushDelivery(pushSender),
        userRepository,
        messageSource,
        preferenceService,
        fanoutWriter,
        actorReader);
  }

  @Test
  void aSecondLikeFromTheSameActorInAGroupWritesNothing() {
    when(repository.existsInGroup(9L, "NOTE_LIKE:5:2026-10-07", 2L, null)).thenReturn(true);

    useCase()
        .record(
            9L,
            NotificationType.NOTE_LIKE,
            2L,
            null,
            new NotificationNoteRef(5L, "hi", null, null),
            "NOTE_LIKE:5:2026-10-07");

    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any(NotificationEntity.class));
    org.mockito.Mockito.verifyNoInteractions(pushSender);
  }

  @Test
  void aRemoteLikeIsStoredWithItsGroupAndPushedUnderTheRemoteHandle() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(new NotificationUser(9L, "me", "ko")));
    when(actorReader.resolveRemote(java.util.Set.of(7L)))
        .thenReturn(
            Map.of(
                7L,
                new NotificationActor(
                    null, "alice@mastodon.social", null, "https://mastodon.social/@alice")));

    useCase()
        .record(
            9L,
            NotificationType.NOTE_LIKE,
            null,
            7L,
            new NotificationNoteRef(5L, "hi", null, null),
            "NOTE_LIKE:5:2026-10-07");

    ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
    org.mockito.Mockito.verify(repository).save(saved.capture());
    assertThat(saved.getValue().getActorRemoteId()).isEqualTo(7L);
    assertThat(saved.getValue().getGroupKey()).isEqualTo("NOTE_LIKE:5:2026-10-07");
    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getValue().body()).isEqualTo("alice@mastodon.social님이 노트를 좋아합니다");
    assertThat(pushed.getValue().subtitle()).isEqualTo("hi");
    assertThat(pushed.getValue().route())
        .isEqualTo(new PushRoute(null, "me", null, null, null, null, null, 5L));
  }

  @Test
  void aReplyPushOpensTheReplyUnderItsWriter() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L))
        .thenReturn(Optional.of(new NotificationUser(2L, "yuki", "ko")));
    when(userRepository.findById(9L)).thenReturn(Optional.of(new NotificationUser(9L, "me", "ko")));

    useCase()
        .record(
            9L,
            NotificationType.NOTE_REPLY,
            2L,
            null,
            new NotificationNoteRef(5L, "hi", 12L, "me too"),
            null);

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getValue().subtitle()).isEqualTo("me too");
    assertThat(pushed.getValue().route())
        .isEqualTo(new PushRoute("yuki", "yuki", null, null, null, null, null, 12L));
    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
        .existsInGroup(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
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
    NotificationUser actor = org.mockito.Mockito.mock(NotificationUser.class);
    when(actor.username()).thenReturn("yuki");
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
            Map.entry(NotificationType.PATH_GREW, "yuki님이 회원님이 속한 컬렉션에 새로 엮었습니다"),
            Map.entry(NotificationType.NOTE_LIKE, "yuki님이 노트를 좋아합니다"),
            Map.entry(NotificationType.NOTE_REPOST, "yuki님이 노트를 리포스트했습니다"),
            Map.entry(NotificationType.NOTE_REPLY, "yuki님이 노트에 답글을 남겼습니다"),
            Map.entry(NotificationType.NOTE_QUOTE, "yuki님이 노트를 인용했습니다"),
            Map.entry(NotificationType.REMOTE_FOLLOW, "yuki님이 다른 서버에서 팔로우하기 시작했습니다"),
            Map.entry(NotificationType.NOTE_MENTION, "yuki님이 노트에서 회원님을 언급했습니다"),
            Map.entry(NotificationType.NOTE_POLL, "투표가 끝났습니다. 결과를 확인해 보세요"));

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
  void pushCarriesTypeAndRouteOfTheTarget() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L))
        .thenReturn(Optional.of(new NotificationUser(2L, "yuki", "ko")));
    when(userRepository.findById(9L)).thenReturn(Optional.of(new NotificationUser(9L, "me", "ko")));

    useCase()
        .record(9L, NotificationType.LIKE, 2L, new NotificationPostRef(10L, "my-post", "Hi", null));
    useCase()
        .record(
            9L,
            NotificationType.MENTION,
            2L,
            new NotificationPostRef(11L, "their-post", "Yo", "mika", 77L, null));
    useCase()
        .record(
            9L,
            NotificationType.SERIES_SUBSCRIBE,
            2L,
            new NotificationSeriesRef(5L, "tokyo-walks", "도쿄 산책"));
    useCase()
        .record(
            9L, NotificationType.CONNECTED, 2L, new NotificationCollectionRef(42L, "산책 모음", 10L));
    useCase().record(9L, NotificationType.FOLLOW, 2L, null);

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender, org.mockito.Mockito.times(5))
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getAllValues())
        .extracting(PushSender.PushMessage::type, PushSender.PushMessage::app)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("LIKE", PushApp.BLOG),
            org.assertj.core.groups.Tuple.tuple("MENTION", PushApp.BLOG),
            org.assertj.core.groups.Tuple.tuple("SERIES_SUBSCRIBE", PushApp.BLOG),
            org.assertj.core.groups.Tuple.tuple("CONNECTED", PushApp.BLOG),
            org.assertj.core.groups.Tuple.tuple("FOLLOW", PushApp.BLOG));
    assertThat(pushed.getAllValues())
        .extracting(PushSender.PushMessage::route)
        .containsExactly(
            new PushRoute("yuki", "me", "my-post", null, null, null, null),
            new PushRoute("yuki", "mika", "their-post", null, null, 77L, null),
            new PushRoute("yuki", "me", null, "tokyo-walks", null, null, null),
            new PushRoute("yuki", null, null, null, 42L, null, null),
            new PushRoute("yuki", null, null, null, null, null, null));
  }

  @Test
  void highlightReplyPushPointsAtTheHighlight() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(2L))
        .thenReturn(Optional.of(new NotificationUser(2L, "yuki", "ko")));
    when(userRepository.findById(9L)).thenReturn(Optional.of(new NotificationUser(9L, "me", "ko")));

    useCase()
        .record(
            9L,
            NotificationType.REPLY,
            2L,
            new NotificationPostRef(11L, "their-post", "Yo", "mika", null, 41L));

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .send(org.mockito.ArgumentMatchers.eq(9L), pushed.capture());
    assertThat(pushed.getValue().route())
        .isEqualTo(new PushRoute("yuki", "mika", "their-post", null, null, null, 41L));
  }

  @Test
  void fannedOutNewPostRoutesToTheActorsPost() {
    when(userRepository.findById(2L))
        .thenReturn(Optional.of(new NotificationUser(2L, "yuki", "ko")));
    when(userRepository.findAllByIdIn(List.of(7L, 8L)))
        .thenReturn(List.of(userWith(7L, "ko"), userWith(8L, "ko")));

    useCase()
        .recordForEach(
            List.of(7L, 8L),
            NotificationType.NEW_POST,
            2L,
            new NotificationPostRef(10L, "fresh", "새 글", null));

    ArgumentCaptor<PushSender.PushMessage> pushed =
        ArgumentCaptor.forClass(PushSender.PushMessage.class);
    org.mockito.Mockito.verify(pushSender)
        .sendToAll(org.mockito.ArgumentMatchers.eq(List.of(7L, 8L)), pushed.capture());
    assertThat(pushed.getValue().route())
        .isEqualTo(new PushRoute("yuki", "yuki", "fresh", null, null, null, null));
  }

  @Test
  void pushIsLocalizedToRecipientLocale() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    NotificationUser actor = org.mockito.Mockito.mock(NotificationUser.class);
    when(actor.username()).thenReturn("yuki");
    when(userRepository.findById(2L)).thenReturn(Optional.of(actor));
    NotificationUser recipient = org.mockito.Mockito.mock(NotificationUser.class);
    when(recipient.locale()).thenReturn("ja");
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

    NotificationUser nameless = org.mockito.Mockito.mock(NotificationUser.class);
    when(nameless.username()).thenReturn(null);
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
  void fanOutChunksToWriterAndPushesOnce() {
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    NotificationUser r7 = userWith(7L, "ko");
    NotificationUser r8 = userWith(8L, "ko");
    NotificationUser r9 = userWith(9L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7, r8, r9));

    NotificationPostRef ref = new NotificationPostRef(10L, "new-post", "새 글", null);
    useCase().recordForEach(List.of(7L, 8L, 9L), NotificationType.NEW_POST, 2L, ref);

    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(fanoutWriter)
        .persistChunk(
            org.mockito.ArgumentMatchers.eq(List.of(7L, 8L, 9L)),
            org.mockito.ArgumentMatchers.eq(NotificationType.NEW_POST),
            org.mockito.ArgumentMatchers.eq(2L),
            json.capture());
    assertThat(json.getValue()).contains("\"slug\":\"new-post\"");
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
    NotificationUser r7 = userWith(7L, "ko");
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
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    NotificationUser r7 = userWith(7L, "ko");
    NotificationUser r9 = userWith(9L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7, r9));
    when(preferenceService.filterEnabled(List.of(7L, 8L, 9L), NotificationType.NEW_POST))
        .thenReturn(List.of(7L, 9L));

    NotificationPostRef ref = new NotificationPostRef(10L, "new-post", "새 글", null);
    useCase().recordForEach(List.of(7L, 8L, 9L), NotificationType.NEW_POST, 2L, ref);

    org.mockito.Mockito.verify(fanoutWriter)
        .persistChunk(
            org.mockito.ArgumentMatchers.eq(List.of(7L, 9L)),
            org.mockito.ArgumentMatchers.eq(NotificationType.NEW_POST),
            org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.anyString());
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
    NotificationUser actor = org.mockito.Mockito.mock(NotificationUser.class);
    when(actor.username()).thenReturn("yuki");
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
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    NotificationUser r7 = userWith(7L, "ko");
    NotificationUser r9 = userWith(9L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7, r9));

    useCase()
        .recordForEach(
            List.of(7L, 9L),
            NotificationType.PATH_GREW,
            2L,
            new NotificationCollectionRef(42L, "긴 여름의 독서", null));

    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(fanoutWriter)
        .persistChunk(
            org.mockito.ArgumentMatchers.eq(List.of(7L, 9L)),
            org.mockito.ArgumentMatchers.eq(NotificationType.PATH_GREW),
            org.mockito.ArgumentMatchers.eq(2L),
            json.capture());
    assertThat(json.getValue())
        .contains("\"collectionId\":42")
        .contains("\"collectionName\":\"긴 여름의 독서\"");
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
    when(userRepository.findById(2L)).thenReturn(Optional.empty());
    NotificationUser r7 = userWith(7L, "ko");
    when(userRepository.findAllByIdIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(r7));
    when(preferenceService.filterEnabled(List.of(7L, 9L), NotificationType.PATH_GREW))
        .thenReturn(List.of(7L));

    useCase()
        .recordForEach(
            List.of(7L, 9L),
            NotificationType.PATH_GREW,
            2L,
            new NotificationCollectionRef(42L, "c", null));

    org.mockito.Mockito.verify(fanoutWriter)
        .persistChunk(
            org.mockito.ArgumentMatchers.eq(List.of(7L)),
            org.mockito.ArgumentMatchers.eq(NotificationType.PATH_GREW),
            org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.anyString());
    org.mockito.Mockito.verify(pushSender)
        .sendToAll(
            org.mockito.ArgumentMatchers.eq(List.of(7L)),
            org.mockito.ArgumentMatchers.any(PushSender.PushMessage.class));
  }
}
