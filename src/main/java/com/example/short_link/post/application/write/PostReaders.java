package com.example.short_link.post.application.write;

import com.example.short_link.common.event.PostReadMilestoneEvent;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostReadMilestoneRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// A reader is a distinct human visitor of the post's page other than its signed-in author. It
// counts under the post's row lock, so each threshold is crossed in one transaction only.
@Component
@RequiredArgsConstructor
public class PostReaders {

  public static final Set<Integer> MILESTONES = Set.of(1, 10, 100, 1000);

  private final PostViewEventRepository views;
  private final PostReadMilestoneRepository milestones;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  // Runs inside the view's transaction with the post locked, before the view itself is saved.
  public void countView(PostEntity post, String visitorHash, boolean bot, Long viewerId) {
    if (bot
        || visitorHash == null
        || post.isOwnedBy(viewerId)
        || views.hasHumanVisit(post.getId(), visitorHash)) {
      return;
    }
    reached(post, post.countReader());
  }

  private void reached(PostEntity post, int readers) {
    if (!MILESTONES.contains(readers)) {
      return;
    }
    Instant now = clock.instant();
    if (milestones.record(post.getId(), readers, now)) {
      events.publishEvent(
          new PostReadMilestoneEvent(
              post.getUserId(), post.getId(), post.getSlug(), post.getTitle(), readers, now));
    }
  }
}
