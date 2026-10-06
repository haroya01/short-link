package com.example.short_link.notification.domain;

// One row of the grouped list: the newest notification of a group stands for it. An ungrouped
// notification is a group of one.
public record NotificationGroup(NotificationEntity newest, long count, boolean unread) {}
