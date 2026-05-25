package com.example.short_link.profile.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.ProfileException;
import org.junit.jupiter.api.Test;

class EventTest {

  @Test
  void normalizesMinimalEvent() {
    String out =
        Event.normalize("{\"title\":\"Workshop\",\"startsAt\":\"2026-06-15T14:00:00+09:00\"}");
    assertThat(out).contains("Workshop");
    assertThat(out).contains("2026-06-15T14:00+09:00");
  }

  @Test
  void normalizesFullEvent() {
    String out =
        Event.normalize(
            "{\"title\":\"Open House\",\"startsAt\":\"2026-06-15T14:00:00+09:00\","
                + "\"endsAt\":\"2026-06-15T17:00:00+09:00\","
                + "\"location\":\"Seoul\","
                + "\"description\":\"Bring friends\","
                + "\"url\":\"https://example.com/info\"}");
    assertThat(out).contains("Open House");
    assertThat(out).contains("\"endsAt\":\"2026-06-15T17:00+09:00\"");
    assertThat(out).contains("Seoul");
    assertThat(out).contains("https://example.com/info");
  }

  @Test
  void requiresTitle() {
    assertThatThrownBy(() -> Event.normalize("{\"startsAt\":\"2026-06-15T14:00:00+09:00\"}"))
        .isInstanceOf(ProfileException.class)
        .hasMessageContaining("title required");
    assertThatThrownBy(
            () -> Event.normalize("{\"title\":\"   \",\"startsAt\":\"2026-06-15T14:00:00+09:00\"}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void requiresStartsAt() {
    assertThatThrownBy(() -> Event.normalize("{\"title\":\"x\"}"))
        .isInstanceOf(ProfileException.class)
        .hasMessageContaining("startsAt required");
    assertThatThrownBy(() -> Event.normalize("{\"title\":\"x\",\"startsAt\":\"  \"}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsBadIso() {
    assertThatThrownBy(() -> Event.normalize("{\"title\":\"x\",\"startsAt\":\"yesterday\"}"))
        .isInstanceOf(ProfileException.class)
        .hasMessageContaining("ISO 8601");
    // Missing offset — OffsetDateTime requires it explicitly.
    assertThatThrownBy(
            () -> Event.normalize("{\"title\":\"x\",\"startsAt\":\"2026-06-15T14:00:00\"}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsEndsAtNotAfterStartsAt() {
    String json =
        "{\"title\":\"x\",\"startsAt\":\"2026-06-15T14:00:00+09:00\","
            + "\"endsAt\":\"2026-06-15T13:00:00+09:00\"}";
    assertThatThrownBy(() -> Event.normalize(json))
        .isInstanceOf(ProfileException.class)
        .hasMessageContaining("after startsAt");

    String sameTime =
        "{\"title\":\"x\",\"startsAt\":\"2026-06-15T14:00:00+09:00\","
            + "\"endsAt\":\"2026-06-15T14:00:00+09:00\"}";
    assertThatThrownBy(() -> Event.normalize(sameTime)).isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsNonHttpUrl() {
    String json =
        "{\"title\":\"x\",\"startsAt\":\"2026-06-15T14:00:00+09:00\","
            + "\"url\":\"javascript:alert(1)\"}";
    assertThatThrownBy(() -> Event.normalize(json))
        .isInstanceOf(ProfileException.class)
        .hasMessageContaining("http");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> Event.normalize("not json")).isInstanceOf(ProfileException.class);
  }

  @Test
  void truncatesLongFields() {
    String longDesc = "x".repeat(700);
    String json =
        "{\"title\":\"x\",\"startsAt\":\"2026-06-15T14:00:00+09:00\","
            + "\"description\":\""
            + longDesc
            + "\"}";
    String out = Event.normalize(json);
    // Should not throw; description gets trimmed to 500.
    assertThat(out).doesNotContain(longDesc);
    assertThat(out).contains("xxxxx");
  }

  @Test
  void preservesOffsetAcrossTimezones() {
    String out = Event.normalize("{\"title\":\"x\",\"startsAt\":\"2026-06-15T14:00:00-08:00\"}");
    assertThat(out).contains("-08:00");
  }

  @Test
  void ignoresUnknownFields() {
    // Forward compat — same class of bug as ContactCard logoFocalX/Y hotfix (PR #256). A frontend
    // shipping recurrence / reminders / capacity ahead of the backend shouldn't 400 every EVENT
    // save just because the record doesn't yet know the field.
    String out =
        Event.normalize(
            "{\"title\":\"x\",\"startsAt\":\"2026-06-15T14:00:00+09:00\","
                + "\"recurrence\":\"FREQ=WEEKLY\",\"capacity\":20}");
    assertThat(out).contains("\"title\":\"x\"");
  }
}
