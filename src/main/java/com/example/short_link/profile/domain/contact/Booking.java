package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * BOOKING block payload — links visitors to an external scheduling/reservation provider (Calendly,
 * Cal.com, 네이버예약, 카카오 톡채널, Microsoft Bookings, Google Calendar appointment, etc.).
 *
 * <p>The host whitelist is the SSRF / phishing guard: only allow-listed providers are accepted, so
 * a malicious user can't dress up an arbitrary URL as "예약하기". Adding a provider is intentional —
 * append a {@link Provider} entry, don't widen the host match.
 *
 * <p>JSON shape: {@code {url, title?, description?, ctaLabel?}}. The provider id is derived from
 * the URL host at render time so we don't have to trust client-supplied "provider" fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Booking(String url, String title, String description, String ctaLabel) {

  private static final int URL_MAX = 512;
  private static final int TITLE_MAX = 60;
  private static final int DESCRIPTION_MAX = 160;
  private static final int CTA_LABEL_MAX = 30;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidUsernameException("booking: config required");
    }
    Booking parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), Booking.class);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("booking: malformed json");
    }
    String url = trimTo(parsed.url, URL_MAX);
    if (url == null) {
      throw new InvalidUsernameException("booking: url required");
    }
    if (Provider.resolve(url).isEmpty()) {
      throw new InvalidUsernameException("booking: unsupported provider");
    }
    Booking out =
        new Booking(
            url,
            trimTo(parsed.title, TITLE_MAX),
            trimTo(parsed.description, DESCRIPTION_MAX),
            trimTo(parsed.ctaLabel, CTA_LABEL_MAX));
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("booking: serialization failed");
    }
  }

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }

  /**
   * Whitelisted booking providers. Add a new provider by appending an entry — never by widening
   * host matching, since the host check is what keeps phishing URLs out of "예약하기" CTAs.
   */
  public enum Provider {
    CALENDLY("calendly", Set.of("calendly.com", "www.calendly.com")),
    CAL_COM("cal_com", Set.of("cal.com", "app.cal.com")),
    GOOGLE_CALENDAR("google_calendar", Set.of("calendar.app.google", "calendar.google.com")),
    NAVER_BOOKING("naver_booking", Set.of("booking.naver.com", "m.booking.naver.com")),
    KAKAO_CHANNEL("kakao_channel", Set.of("pf.kakao.com")),
    MICROSOFT_BOOKINGS(
        "microsoft_bookings",
        Set.of("outlook.office.com", "outlook.office365.com", "bookwithme.microsoft.com")),
    TIDYCAL("tidycal", Set.of("tidycal.com", "www.tidycal.com")),
    ACUITY("acuity", Set.of("app.acuityscheduling.com")),
    CATCHTABLE("catchtable", Set.of("app.catchtable.co.kr", "catchtable.co.kr"));

    private final String id;
    private final Set<String> hosts;

    Provider(String id, Set<String> hosts) {
      this.id = id;
      this.hosts = hosts;
    }

    public String id() {
      return id;
    }

    public static java.util.Optional<Provider> resolve(String url) {
      if (url == null || url.isBlank()) return java.util.Optional.empty();
      URI uri;
      try {
        uri = URI.create(url.trim());
      } catch (IllegalArgumentException ex) {
        return java.util.Optional.empty();
      }
      String scheme = uri.getScheme();
      if (scheme == null) return java.util.Optional.empty();
      String s = scheme.toLowerCase(Locale.ROOT);
      if (!s.equals("http") && !s.equals("https")) return java.util.Optional.empty();
      String host = uri.getHost();
      if (host == null || host.isBlank()) return java.util.Optional.empty();
      String h = host.toLowerCase(Locale.ROOT);
      for (Provider p : values()) {
        if (p.hosts.contains(h)) return java.util.Optional.of(p);
      }
      return java.util.Optional.empty();
    }
  }
}
