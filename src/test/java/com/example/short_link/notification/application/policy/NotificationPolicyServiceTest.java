package com.example.short_link.notification.application.policy;

import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.ACCEPT;
import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.DROP;
import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.FILTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.policy.FilteredSender;
import com.example.short_link.notification.domain.policy.NotificationPolicy;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationPolicyRepository;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPolicyServiceTest {

  @Mock private NotificationPolicyRepository policies;
  @Mock private NotificationRepository notifications;
  @Mock private NotificationActorReader actors;

  private NotificationPolicyService service() {
    return new NotificationPolicyService(policies, notifications, actors);
  }

  @Test
  void aMemberWhoNeverChoseHasMastodonsDefaults() {
    when(policies.find(7L)).thenReturn(Optional.empty());

    assertThat(service().policy(7L)).isEqualTo(NotificationPolicy.DEFAULT);
  }

  @Test
  void anUpdateKeepsWhatItDoesNotName() {
    when(policies.find(7L))
        .thenReturn(Optional.of(new NotificationPolicy(FILTER, ACCEPT, ACCEPT, FILTER)));

    NotificationPolicy saved = service().update(7L, null, DROP, null, ACCEPT);

    assertThat(saved).isEqualTo(new NotificationPolicy(FILTER, DROP, ACCEPT, ACCEPT));
    verify(policies).save(7L, saved);
  }

  @Test
  void keptNoticesAreGroupedBySenderWithAccountsThatAreGoneLeftOut() {
    Instant at = Instant.parse("2026-10-08T00:00:00Z");
    when(notifications.filteredSenders(7L, NotificationPolicyService.REQUESTS_LIMIT))
        .thenReturn(
            List.of(
                new FilteredSender(2L, null, 3L, at),
                new FilteredSender(null, 40L, 1L, at),
                new FilteredSender(5L, null, 1L, at)));
    when(actors.resolve(List.of(2L, 5L)))
        .thenReturn(Map.of(2L, new NotificationActor(2L, "sori", null)));
    when(actors.resolveRemote(List.of(40L)))
        .thenReturn(
            Map.of(
                40L,
                new NotificationActor(
                    null, "carol@fosstodon.org", null, "https://fosstodon.org/@carol", 40L)));

    List<NotificationPolicyService.Request> requests = service().requests(7L);

    assertThat(requests)
        .extracting(r -> r.actor().username(), NotificationPolicyService.Request::count)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("sori", 3L),
            org.assertj.core.groups.Tuple.tuple("carol@fosstodon.org", 1L));
  }

  @Test
  void anEmptyInboxResolvesNoOne() {
    when(notifications.filteredSenders(7L, NotificationPolicyService.REQUESTS_LIMIT))
        .thenReturn(List.of());

    assertThat(service().requests(7L)).isEmpty();
    verifyNoInteractions(actors);
  }

  @Test
  void acceptingASenderLetsTheirNoticesInAndEveryLaterOneThrough() {
    service().accept(7L, null, 40L);

    verify(policies).permit(7L, null, 40L);
    verify(notifications).unfilter(7L, null, 40L);
  }

  @Test
  void dismissingThrowsTheKeptNoticesAway() {
    service().dismiss(7L, 2L, null);

    verify(notifications).deleteFiltered(7L, 2L, null);
    verifyNoInteractions(policies);
  }

  @Test
  void aRequestNamesExactlyOneSender() {
    NotificationPolicyService service = service();

    assertThatThrownBy(() -> service.accept(7L, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.dismiss(7L, 2L, 40L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
