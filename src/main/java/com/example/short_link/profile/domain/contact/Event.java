package com.example.short_link.profile.domain.contact;

import static com.example.short_link.profile.domain.contact.BlockConfig.trimTo;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Event(
    String title, String startsAt, String endsAt, String location, String description, String url) {

  private static final int TITLE_MAX = 80;
  private static final int LOCATION_MAX = 120;
  private static final int DESCRIPTION_MAX = 500;
  private static final int URL_MAX = 512;

  public static String normalize(String raw) {
    Event parsed = BlockConfig.read(raw, Event.class, "event");
    String title = trimTo(parsed.title, TITLE_MAX);
    if (title == null) {
      throw BlockConfig.invalid("event: title required");
    }
    String startsAtRaw = parsed.startsAt == null ? "" : parsed.startsAt.trim();
    if (startsAtRaw.isEmpty()) {
      throw BlockConfig.invalid("event: startsAt required");
    }
    OffsetDateTime start = parseIso(startsAtRaw, "startsAt");
    String endsAt = endsAt(parsed.endsAt, start);
    String url = trimTo(parsed.url, URL_MAX);
    if (url != null) {
      BlockConfig.requireHttpUrl(url, "event: url");
    }
    Event out =
        new Event(
            title,
            start.toString(),
            endsAt,
            trimTo(parsed.location, LOCATION_MAX),
            trimTo(parsed.description, DESCRIPTION_MAX),
            url);
    return BlockConfig.write(out, "event");
  }

  private static String endsAt(String raw, OffsetDateTime start) {
    String endsAtRaw = raw == null ? "" : raw.trim();
    if (endsAtRaw.isEmpty()) {
      return null;
    }
    OffsetDateTime end = parseIso(endsAtRaw, "endsAt");
    if (!end.isAfter(start)) {
      throw BlockConfig.invalid("event: endsAt must be after startsAt");
    }
    return end.toString();
  }

  private static OffsetDateTime parseIso(String value, String field) {
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME,
          "event: " + field + " must be ISO 8601 with offset (e.g. 2026-06-15T14:00:00+09:00)");
    }
  }
}
