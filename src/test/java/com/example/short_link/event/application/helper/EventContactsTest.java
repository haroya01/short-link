package com.example.short_link.event.application.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.exception.EventException;
import org.junit.jupiter.api.Test;

class EventContactsTest {

  @Test
  void email_normalizedToLowercase() {
    assertThat(EventContacts.normalize(ContactField.EMAIL, "  User@Example.COM "))
        .isEqualTo("user@example.com");
  }

  @Test
  void email_invalidRejected() {
    assertThatThrownBy(() -> EventContacts.normalize(ContactField.EMAIL, "not-an-email"))
        .isInstanceOf(EventException.class);
    assertThatThrownBy(() -> EventContacts.normalize(ContactField.EMAIL, null))
        .isInstanceOf(EventException.class);
  }

  @Test
  void phone_strippedOfSeparators() {
    assertThat(EventContacts.normalize(ContactField.PHONE, "010-1234-5678"))
        .isEqualTo("01012345678");
    assertThat(EventContacts.normalize(ContactField.PHONE, "+81 90 1234 5678"))
        .isEqualTo("+819012345678");
  }

  @Test
  void kakao_idPattern() {
    assertThat(EventContacts.normalize(ContactField.KAKAO, "my_id.99")).isEqualTo("my_id.99");
    assertThatThrownBy(() -> EventContacts.normalize(ContactField.KAKAO, "한글아이디"))
        .isInstanceOf(EventException.class);
  }
}
