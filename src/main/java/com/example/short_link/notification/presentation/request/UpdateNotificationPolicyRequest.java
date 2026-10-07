package com.example.short_link.notification.presentation.request;

import com.example.short_link.notification.domain.policy.NotificationPolicyLevel;

public record UpdateNotificationPolicyRequest(
    NotificationPolicyLevel forNotFollowing,
    NotificationPolicyLevel forNotFollowers,
    NotificationPolicyLevel forNewAccounts,
    NotificationPolicyLevel forPrivateMentions) {}
