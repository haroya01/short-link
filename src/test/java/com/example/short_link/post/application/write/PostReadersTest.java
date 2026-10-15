package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.PostReadMilestoneEvent;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostReadMilestoneRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class PostReadersTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

  private final PostViewEventRepository views = mock(PostViewEventRepository.class);
  private final PostReadMilestoneRepository milestones = mock(PostReadMilestoneRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final PostReaders readers =
      new PostReaders(views, milestones, events, Clock.fixed(NOW, ZoneOffset.UTC));

  private static PostEntity post(int readersSoFar) {
    PostEntity post = new PostEntity(7L, "3", "제목", "ko");
    post.publish();
    ReflectionTestUtils.setField(post, "id", 30L);
    ReflectionTestUtils.setField(post, "humanReaderCount", readersSoFar);
    return post;
  }

  @Test
  void theFirstHumanReaderIsAnnouncedOnce() {
    PostEntity post = post(0);
    when(milestones.record(30L, 1, NOW)).thenReturn(true);

    readers.countView(post, "visitor", false, null);

    assertThat(post.getHumanReaderCount()).isEqualTo(1);
    ArgumentCaptor<PostReadMilestoneEvent> event =
        ArgumentCaptor.forClass(PostReadMilestoneEvent.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue()).isEqualTo(new PostReadMilestoneEvent(7L, 30L, "3", "제목", 1, NOW));
  }

  @Test
  void onlyThresholdsAreRecordedAndAnAlreadyRecordedOneIsSilent() {
    PostEntity post = post(1);
    readers.countView(post, "visitor", false, null);
    verify(milestones, never()).record(anyLong(), anyInt(), any());

    PostEntity nine = post(9);
    when(milestones.record(30L, 10, NOW)).thenReturn(false);
    readers.countView(nine, "visitor", false, null);
    assertThat(nine.getHumanReaderCount()).isEqualTo(10);
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void botsHashlessVisitsTheAuthorAndReturningVisitorsAreNotReaders() {
    PostEntity post = post(0);
    readers.countView(post, "v", true, null);
    readers.countView(post, null, false, null);
    readers.countView(post, "v", false, 7L);
    when(views.hasHumanVisit(30L, "back")).thenReturn(true);
    readers.countView(post, "back", false, 8L);

    assertThat(post.getHumanReaderCount()).isZero();
    verifyNoInteractions(milestones, events);
  }
}
