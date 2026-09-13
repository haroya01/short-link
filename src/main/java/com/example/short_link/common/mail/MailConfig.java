package com.example.short_link.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** SMTP 설정과 {@code short-link.mail.enabled=true}가 있을 때만 발송한다. 그 외에는 자격증명 없이 실행할 수 있도록 로그만 남긴다. */
@Slf4j
@Configuration
public class MailConfig {

  @Bean
  @ConditionalOnProperty(name = "short-link.mail.enabled", havingValue = "true")
  MailSender smtpMailSender(
      JavaMailSender javaMailSender,
      @org.springframework.beans.factory.annotation.Value("${short-link.mail.from}") String from) {
    return (to, subject, body) -> {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      javaMailSender.send(message);
    };
  }

  @Bean
  @ConditionalOnMissingBean(MailSender.class)
  MailSender loggingMailSender() {
    return (to, subject, body) ->
        log.info("mail disabled — would send to={} subject={}", to, subject);
  }
}
