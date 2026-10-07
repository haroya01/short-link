package com.example.short_link.notification.application.policy;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.policy.FilteredSender;
import com.example.short_link.notification.domain.policy.NotificationPolicy;
import com.example.short_link.notification.domain.policy.NotificationPolicyLevel;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationPolicyRepository;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The member's notification policy and the notices it kept aside, grouped by sender as Mastodon's
// filtered notifications: accepting a sender lets their kept notices into the list and every later
// one through; dismissing throws the kept ones away.
@Service
@RequiredArgsConstructor
public class NotificationPolicyService {

  static final int REQUESTS_LIMIT = 40;

  private final NotificationPolicyRepository policies;
  private final NotificationRepository notifications;
  private final NotificationActorReader actors;

  public record Request(NotificationActor actor, long count, Instant lastAt) {}

  @Transactional(readOnly = true)
  public NotificationPolicy policy(Long userId) {
    return policies.find(userId).orElse(NotificationPolicy.DEFAULT);
  }

  @Transactional
  public NotificationPolicy update(
      Long userId,
      NotificationPolicyLevel notFollowing,
      NotificationPolicyLevel notFollowers,
      NotificationPolicyLevel newAccounts,
      NotificationPolicyLevel privateMentions) {
    NotificationPolicy next =
        policies
            .find(userId)
            .orElse(NotificationPolicy.DEFAULT)
            .merge(notFollowing, notFollowers, newAccounts, privateMentions);
    policies.save(userId, next);
    return next;
  }

  @Transactional(readOnly = true)
  public List<Request> requests(Long userId) {
    List<FilteredSender> senders = notifications.filteredSenders(userId, REQUESTS_LIMIT);
    if (senders.isEmpty()) {
      return List.of();
    }
    Map<Long, NotificationActor> members =
        actors.resolve(
            senders.stream().map(FilteredSender::actorUserId).filter(Objects::nonNull).toList());
    Map<Long, NotificationActor> remotes =
        actors.resolveRemote(
            senders.stream().map(FilteredSender::actorRemoteId).filter(Objects::nonNull).toList());
    return senders.stream()
        .map(
            sender -> {
              NotificationActor actor =
                  sender.actorUserId() != null
                      ? members.get(sender.actorUserId())
                      : remotes.get(sender.actorRemoteId());
              return actor == null ? null : new Request(actor, sender.count(), sender.lastAt());
            })
        .filter(Objects::nonNull)
        .toList();
  }

  @Transactional
  public void accept(Long userId, Long actorUserId, Long actorRemoteId) {
    requireSender(actorUserId, actorRemoteId);
    policies.permit(userId, actorUserId, actorRemoteId);
    notifications.unfilter(userId, actorUserId, actorRemoteId);
  }

  @Transactional
  public void dismiss(Long userId, Long actorUserId, Long actorRemoteId) {
    requireSender(actorUserId, actorRemoteId);
    notifications.deleteFiltered(userId, actorUserId, actorRemoteId);
  }

  private static void requireSender(Long actorUserId, Long actorRemoteId) {
    if ((actorUserId == null) == (actorRemoteId == null)) {
      throw new IllegalArgumentException("exactly one of actorUserId and actorRemoteId");
    }
  }
}
