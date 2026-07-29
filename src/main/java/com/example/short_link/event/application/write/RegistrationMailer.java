package com.example.short_link.event.application.write;

import com.example.short_link.common.mail.MailSender;
import com.example.short_link.event.application.helper.EventPublicUrlBuilder;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 신청 확인 메일 (연락처가 이메일인 이벤트만). 실패해도 신청은 유효 — best-effort. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationMailer {

  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final MailSender mailSender;
  private final EventPublicUrlBuilder urlBuilder;

  public void sendConfirmation(
      EventEntity event, EventRegistrationEntity registration, String cancelToken) {
    if (event.getContactField() != ContactField.EMAIL) return;
    try {
      String when =
          WHEN.withZone(zoneOf(event.getTimezone())).format(event.getStartsAt())
              + " ("
              + event.getTimezone()
              + ")";
      String pageUrl = urlBuilder.build(event.getSlug());
      String cancelUrl = pageUrl + "?cancel=" + cancelToken;
      String body =
          """
          %s 님, 신청이 완료되었습니다.

          이벤트: %s
          일시: %s
          페이지: %s

          참석이 어려워지면 아래 링크로 취소할 수 있어요:
          %s

          — kurl
          """
              .formatted(registration.getName(), event.getTitle(), when, pageUrl, cancelUrl);
      mailSender.send(registration.getContact(), "[kurl] 신청 확인 — " + event.getTitle(), body);
    } catch (RuntimeException e) {
      log.warn(
          "confirmation mail skipped for registration {}: {}",
          registration.getId(),
          e.getMessage());
    }
  }

  private static ZoneId zoneOf(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (Exception e) {
      return ZoneId.of("Asia/Seoul");
    }
  }
}
