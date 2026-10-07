package com.example.short_link.notification.domain.policy;

import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.ACCEPT;
import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.DROP;
import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.FILTER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationPolicyTest {

  private static NotificationSender sender(
      NotificationPolicy policy,
      boolean following,
      boolean follower,
      boolean newAccount,
      boolean privateMention) {
    return new NotificationSender(
        true, policy, false, following, follower, newAccount, privateMention);
  }

  @Test
  void byDefaultOnlyUnsolicitedPrivateMentionsAreKeptAside() {
    assertThat(NotificationPolicy.verdict(sender(null, false, false, true, false)))
        .isEqualTo(ACCEPT);
    assertThat(NotificationPolicy.verdict(sender(null, false, false, false, true)))
        .isEqualTo(FILTER);
    assertThat(NotificationPolicy.verdict(sender(null, true, false, false, true)))
        .isEqualTo(ACCEPT);
  }

  @Test
  void theHarshestMatchingCategoryWins() {
    NotificationPolicy policy = new NotificationPolicy(FILTER, ACCEPT, DROP, ACCEPT);

    assertThat(NotificationPolicy.verdict(sender(policy, false, true, false, false)))
        .isEqualTo(FILTER);
    assertThat(NotificationPolicy.verdict(sender(policy, false, true, true, false)))
        .isEqualTo(DROP);
    assertThat(NotificationPolicy.verdict(sender(policy, true, false, false, false)))
        .isEqualTo(ACCEPT);
  }

  @Test
  void anAcceptedSenderAlwaysGetsThroughButATurnedOffTypeNever() {
    NotificationPolicy strict = new NotificationPolicy(DROP, DROP, DROP, DROP);

    assertThat(
            NotificationPolicy.verdict(
                new NotificationSender(true, strict, true, false, false, true, true)))
        .isEqualTo(ACCEPT);
    assertThat(
            NotificationPolicy.verdict(
                new NotificationSender(false, null, true, true, true, false, false)))
        .isEqualTo(DROP);
  }

  @Test
  void anUpdateChangesOnlyWhatItNames() {
    assertThat(NotificationPolicy.DEFAULT.merge(null, FILTER, null, null))
        .isEqualTo(new NotificationPolicy(ACCEPT, FILTER, ACCEPT, FILTER));
  }
}
