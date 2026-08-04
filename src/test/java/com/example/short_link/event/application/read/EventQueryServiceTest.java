package com.example.short_link.event.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.image.EventCoverImageService;
import com.example.short_link.event.application.read.EventView.EventLinkView;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.QuestionType;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventQueryServiceTest {

  private static final Instant CREATED = Instant.parse("2026-08-01T09:00:00Z");

  @Mock private EventRepository eventRepository;
  @Mock private EventQuestionRepository questionRepository;
  @Mock private EventRegistrationRepository registrationRepository;
  @Mock private EventLinkRepository eventLinkRepository;
  @Mock private LinkRepository linkRepository;
  @Mock private EventCoverImageService coverImageService;

  private EventQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new EventQueryService(
            eventRepository,
            questionRepository,
            registrationRepository,
            eventLinkRepository,
            linkRepository,
            coverImageService);
  }

  private EventEntity ownedEvent() {
    EventEntity event =
        new EventEntity(
            1L,
            "slug123abc",
            "스터디",
            null,
            Instant.parse("2026-09-01T10:00:00Z"),
            null,
            "Asia/Seoul",
            null,
            null,
            null,
            null,
            null,
            ContactField.EMAIL);
    ReflectionTestUtils.setField(event, "id", 10L);
    return event;
  }

  private EventRegistrationEntity registration(long id, String name, String contact) {
    EventRegistrationEntity registration =
        new EventRegistrationEntity(10L, name, contact, null, "hash");
    ReflectionTestUtils.setField(registration, "id", id);
    ReflectionTestUtils.setField(registration, "createdAt", CREATED);
    return registration;
  }

  @Test
  void listMyEvents_mapsCoverUrl() {
    EventEntity event = ownedEvent();
    when(eventRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(event));
    when(coverImageService.urlFor(null)).thenReturn(null);

    List<EventView> views = service.listMyEvents(1L);

    assertThat(views).hasSize(1);
    assertThat(views.get(0).slug()).isEqualTo("slug123abc");
    assertThat(views.get(0).links()).isEmpty();
  }

  @Test
  void findOwnEvent_ofSomeoneElse_isDenied() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));

    assertThatThrownBy(() -> service.findOwnEvent(99L, 10L))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_PERMISSION_DENIED);
  }

  @Test
  void findOwnEvent_missing_isNotFound() {
    when(eventRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findOwnEvent(1L, 10L))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  void findOwnEvent_resolvesShortCodesBestEffort() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
    when(questionRepository.findAllByEventIdOrderByPosition(10L))
        .thenReturn(
            List.of(
                new EventQuestionEntity(10L, 0, QuestionType.SHORT_TEXT, "한 줄 소개", null, true)));
    when(eventLinkRepository.findAllByEventId(10L))
        .thenReturn(
            List.of(new EventLinkEntity(10L, 7L, "기본"), new EventLinkEntity(10L, 8L, "단톡용")));
    LinkEntity link = mock(LinkEntity.class);
    when(link.getShortCode()).thenReturn(ShortCode.of("abc1234"));
    when(linkRepository.findById(7L)).thenReturn(Optional.of(link));
    when(linkRepository.findById(8L)).thenReturn(Optional.empty());

    EventView view = service.findOwnEvent(1L, 10L);

    assertThat(view.questions()).extracting(EventQuestionView::label).containsExactly("한 줄 소개");
    assertThat(view.links())
        .containsExactly(
            new EventLinkView(7L, "abc1234", "기본"), new EventLinkView(8L, null, "단톡용"));
  }

  @Test
  void listAttendees_labelsChannelWithFallbacks() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
    when(eventLinkRepository.findAllByEventId(10L))
        .thenReturn(List.of(new EventLinkEntity(10L, 7L, "단톡용")));

    EventRegistrationEntity byLink = registration(1L, "가", "a@x.com");
    byLink.attribute(7L, "messenger", "kakaotalk", "kakao.example", null, "vh");
    EventRegistrationEntity unknownLink = registration(2L, "나", "b@x.com");
    unknownLink.attribute(99L, null, "instagram", null, null, null);
    EventRegistrationEntity byReferrer = registration(3L, "다", "c@x.com");
    byReferrer.attribute(null, null, null, "twitter.example", null, null);
    EventRegistrationEntity direct = registration(4L, "라", "d@x.com");
    when(registrationRepository.findAllByEventIdOrderByCreatedAtAsc(10L))
        .thenReturn(List.of(byLink, unknownLink, byReferrer, direct));

    List<AttendeeView> attendees = service.listAttendees(1L, 10L);

    assertThat(attendees)
        .extracting(AttendeeView::channel)
        .containsExactly("단톡용", "instagram", "twitter.example", null);
  }

  @Test
  void exportAttendeesCsv_escapesAndAlignsAnswers() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
    when(eventLinkRepository.findAllByEventId(10L)).thenReturn(List.of());
    EventQuestionEntity question =
        new EventQuestionEntity(10L, 0, QuestionType.SHORT_TEXT, "소속, 팀", null, false);
    ReflectionTestUtils.setField(question, "id", 5L);
    when(questionRepository.findAllByEventIdOrderByPosition(10L)).thenReturn(List.of(question));

    EventRegistrationEntity answered =
        new EventRegistrationEntity(10L, "김\"댓\"글", "a@x.com", "{\"5\":\"kurl\"}", "hash");
    ReflectionTestUtils.setField(answered, "id", 1L);
    ReflectionTestUtils.setField(answered, "createdAt", CREATED);
    EventRegistrationEntity unanswered = registration(2L, "나\n다", "b@x.com");
    when(registrationRepository.findAllByEventIdOrderByCreatedAtAsc(10L))
        .thenReturn(List.of(answered, unanswered));

    String csv = service.exportAttendeesCsv(1L, 10L);
    String[] lines = csv.split("\n", 3);

    assertThat(lines[0]).isEqualTo("name,contact,status,channel,registered_at,\"소속, 팀\"");
    assertThat(lines[1]).startsWith("\"김\"\"댓\"\"글\",a@x.com,CONFIRMED,,").endsWith(",kurl");
    // 이름 안의 개행은 셀 안에 인용된 채 남는다 — 행 분리와 헷갈리면 안 된다.
    assertThat(lines[2]).startsWith("\"나\n다\",b@x.com,CONFIRMED,,");
  }
}
