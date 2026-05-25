package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * EVENT block payload — user-authored calendar event (workshop, popup, deadline, etc.) that
 * visitors can add to their own calendar. Rendered as a date-tile card with an "Add to Calendar"
 * action; ICS file generation + Google/Outlook URL building happen on the frontend so we don't need
 * to round-trip for a download.
 *
 * <p>JSON shape: {@code {title, startsAt, endsAt?, location?, description?, url?}}. Times are ISO
 * 8601 with offset (e.g. {@code 2026-06-15T14:00:00+09:00}) — we store the offset, not just UTC, so
 * the rendered card can display "9 AM KST" without re-resolving timezones every render. The
 * frontend can convert to the visitor's local time at display time if it wants.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Event(
    String title, String startsAt, String endsAt, String location, String description, String url) {

  private static final int TITLE_MAX = 80;
  private static final int LOCATION_MAX = 120;
  private static final int DESCRIPTION_MAX = 500;
  private static final int URL_MAX = 512;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: config required");
    }
    Event parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), Event.class);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: malformed json");
    }
    String title = trimTo(parsed.title, TITLE_MAX);
    if (title == null) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: title required");
    }
    String startsAtRaw = parsed.startsAt == null ? "" : parsed.startsAt.trim();
    if (startsAtRaw.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: startsAt required");
    }
    OffsetDateTime start = parseIso(startsAtRaw, "startsAt");

    String endsAtRaw = parsed.endsAt == null ? "" : parsed.endsAt.trim();
    String endsAtOut = null;
    if (!endsAtRaw.isEmpty()) {
      OffsetDateTime end = parseIso(endsAtRaw, "endsAt");
      if (!end.isAfter(start)) {
        throw new ProfileException(
            ProfileErrorCode.INVALID_USERNAME, "event: endsAt must be after startsAt");
      }
      endsAtOut = end.toString();
    }

    String url = trimTo(parsed.url, URL_MAX);
    if (url != null) validateHttpUrl(url);

    Event out =
        new Event(
            title,
            start.toString(),
            endsAtOut,
            trimTo(parsed.location, LOCATION_MAX),
            trimTo(parsed.description, DESCRIPTION_MAX),
            url);
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: serialization failed");
    }
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

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }

  private static void validateHttpUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "event: url missing host");
    }
  }
}
