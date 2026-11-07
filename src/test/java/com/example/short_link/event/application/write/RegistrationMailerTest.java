package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.mail.MailSender;
import com.example.short_link.event.application.helper.EventPublicUrlBuilder;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RegistrationMailerTest {

  private final MailSender mail = mock(MailSender.class);
  private final RegistrationMailer mailer =
      new RegistrationMailer(mail, new EventPublicUrlBuilder("https://kurl.me"));

  private static EventEntity event(ContactField contact) {
    return new EventEntity(
        1L,
        "slug123abc",
        "스터디",
        null,
        Instant.parse("2026-12-01T10:00:00Z"),
        null,
        "Asia/Tokyo",
        null,
        null,
        null,
        10,
        null,
        contact);
  }

  private static EventRegistrationEntity registration(String contact) {
    return new EventRegistrationEntity(42L, "김철수", contact, null, "hash");
  }

  @Test
  void aConfirmationGoesToTheEmailWithTheCancelLink() {
    mailer.sendConfirmation(event(ContactField.EMAIL), registration("a@b.com"), "token");

    verify(mail)
        .send(
            eq("a@b.com"), contains("스터디"), contains("https://kurl.me/e/slug123abc?cancel=token"));
  }

  @Test
  void aMailThatCannotBeSentNeverFailsTheRegistrationThatAlreadyCommitted() {
    doThrow(new IllegalStateException("smtp down"))
        .when(mail)
        .send(anyString(), anyString(), anyString());

    assertThatCode(
            () -> mailer.sendConfirmation(event(ContactField.EMAIL), registration("a@b.com"), "t"))
        .doesNotThrowAnyException();
  }

  @Test
  void anEventThatAsksForAPhoneOrAnotherContactSendsNoMail() {
    mailer.sendConfirmation(event(ContactField.PHONE), registration("010-0000-0000"), "t");

    verify(mail, never()).send(anyString(), anyString(), anyString());
  }
}
