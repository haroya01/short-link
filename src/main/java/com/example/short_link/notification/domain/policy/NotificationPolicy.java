package com.example.short_link.notification.domain.policy;

import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.ACCEPT;
import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.DROP;
import static com.example.short_link.notification.domain.policy.NotificationPolicyLevel.FILTER;

// Mastodon's notification policy: for notices from people the member doesn't follow, people who
// don't follow them (or followed less than three days ago), accounts younger than thirty days, and
// private mentions they didn't ask for, accept them, keep them aside (filter), or drop them. A
// sender the member accepted from the filtered inbox always gets through. Accounts on servers
// moderators limited are already kept quiet for members who don't follow them.
public record NotificationPolicy(
    NotificationPolicyLevel forNotFollowing,
    NotificationPolicyLevel forNotFollowers,
    NotificationPolicyLevel forNewAccounts,
    NotificationPolicyLevel forPrivateMentions) {

  public static final NotificationPolicy DEFAULT =
      new NotificationPolicy(ACCEPT, ACCEPT, ACCEPT, FILTER);

  public NotificationPolicy merge(
      NotificationPolicyLevel notFollowing,
      NotificationPolicyLevel notFollowers,
      NotificationPolicyLevel newAccounts,
      NotificationPolicyLevel privateMentions) {
    return new NotificationPolicy(
        notFollowing == null ? forNotFollowing : notFollowing,
        notFollowers == null ? forNotFollowers : notFollowers,
        newAccounts == null ? forNewAccounts : newAccounts,
        privateMentions == null ? forPrivateMentions : privateMentions);
  }

  public static NotificationPolicyLevel verdict(NotificationSender sender) {
    if (!sender.enabled()) {
      return DROP;
    }
    if (sender.permitted()) {
      return ACCEPT;
    }
    NotificationPolicy policy = sender.policy() == null ? DEFAULT : sender.policy();
    NotificationPolicyLevel verdict = ACCEPT;
    if (!sender.following()) {
      verdict = worse(verdict, policy.forNotFollowing());
    }
    if (!sender.follower()) {
      verdict = worse(verdict, policy.forNotFollowers());
    }
    if (sender.newAccount()) {
      verdict = worse(verdict, policy.forNewAccounts());
    }
    if (sender.privateMention() && !sender.following()) {
      verdict = worse(verdict, policy.forPrivateMentions());
    }
    return verdict;
  }

  private static NotificationPolicyLevel worse(
      NotificationPolicyLevel a, NotificationPolicyLevel b) {
    return a.ordinal() >= b.ordinal() ? a : b;
  }
}
