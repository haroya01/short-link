package com.example.short_link.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 트랜잭션 메일 포트. SMTP 설정(spring.mail.*)과 {@code short-link.mail.enabled=true} 가 있으면 실발송, 없으면 로그만 남기는
 * no-op — 로컬/CI 가 메일 자격증명 없이 그대로 돈다. SES 는 SMTP 엔드포인트로 붙는다.
 */
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
