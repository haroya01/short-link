package com.example.short_link.common.mail;

public interface MailSender {

  void send(String to, String subject, String body);
}
