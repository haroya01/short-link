package com.example.short_link.event.application.helper;

import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.util.regex.Pattern;

/** 연락처 정규화 + 필드 타입별 검증. UNIQUE(event_id, contact) 중복 판정의 기준값을 만든다. */
public final class EventContacts {

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 .-]{5,18}[0-9]$");
  private static final Pattern KAKAO = Pattern.compile("^[A-Za-z0-9._-]{2,30}$");

  private EventContacts() {}

  public static String normalize(ContactField field, String raw) {
    if (raw == null || raw.isBlank()) {
      throw new EventException(EventErrorCode.INVALID_CONTACT, field);
    }
    String value = raw.trim();
    boolean valid =
        switch (field) {
          case EMAIL -> EMAIL.matcher(value).matches();
          case PHONE -> PHONE.matcher(value.replaceAll("\\s", "")).matches();
          case KAKAO -> KAKAO.matcher(value).matches();
        };
    if (!valid) {
      throw new EventException(EventErrorCode.INVALID_CONTACT, field);
    }
    return switch (field) {
      case EMAIL -> value.toLowerCase();
      case PHONE -> value.replaceAll("[\\s.-]", "");
      case KAKAO -> value;
    };
  }
}
