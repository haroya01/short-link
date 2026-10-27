package com.example.short_link.common.notification;

// link decides that a link has something to tell its owner; the notification slice keeps the inbox
// row and the push.
public interface LinkNoticeSender {

  void send(LinkNotice notice);
}
