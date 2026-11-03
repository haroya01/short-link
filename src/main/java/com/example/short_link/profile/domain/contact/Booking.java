package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// Only allow-listed booking hosts are accepted to keep arbitrary phishing URLs out of reservation
// CTAs. Derive provider identity from the URL host, not client input; add providers through
// Provider entries.
@JsonIgnoreProperties(ignoreUnknown = true)
public record Booking(String url, String title, String description, String ctaLabel) {

  private static final int URL_MAX = 512;
  private static final int TITLE_MAX = 60;
  private static final int DESCRIPTION_MAX = 160;
  private static final int CTA_LABEL_MAX = 30;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "booking: config required");
    }
    Booking parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), Booking.class);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "booking: malformed json");
    }
    String url = trimTo(parsed.url, URL_MAX);
    if (url == null) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "booking: url required");
    }
    if (Provider.resolve(url).isEmpty()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "booking: unsupported provider");
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
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "booking: serialization failed");
    }
  }

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }

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
    CATCHTABLE("catchtable", Set.of("app.catchtable.co.kr", "catchtable.co.kr")),
    HOTPEPPER_BEAUTY("hotpepper_beauty", Set.of("beauty.hotpepper.jp")),
    HOTPEPPER_GOURMET("hotpepper_gourmet", Set.of("www.hotpepper.jp")),
    TABELOG("tabelog", Set.of("tabelog.com", "s.tabelog.com")),
    TABLECHECK("tablecheck", Set.of("www.tablecheck.com", "tablecheck.com")),
    AIRRESERVE("airreserve", Set.of("airrsv.net")),
    RESERVA("reserva", Set.of("reserva.be")),
    STORES_RESERVE("stores_reserve", Set.of("coubic.com"), ".stores.jp", "/reserve/"),
    IKYU_RESTAURANT("ikyu_restaurant", Set.of("restaurant.ikyu.com"));

    private final String id;
    private final Set<String> hosts;
    private final String subdomainOf;
    private final String subdomainPath;

    Provider(String id, Set<String> hosts) {
      this(id, hosts, null, null);
    }

    Provider(String id, Set<String> hosts, String subdomainOf, String subdomainPath) {
      this.id = id;
      this.hosts = hosts;
      this.subdomainOf = subdomainOf;
      this.subdomainPath = subdomainPath;
    }

    public String id() {
      return id;
    }

    public static Optional<Provider> resolve(String url) {
      if (url == null || url.isBlank()) return Optional.empty();
      URI uri;
      try {
        uri = URI.create(url.trim());
      } catch (IllegalArgumentException ex) {
        return Optional.empty();
      }
      String scheme = uri.getScheme();
      if (scheme == null) return Optional.empty();
      String s = scheme.toLowerCase(Locale.ROOT);
      if (!s.equals("http") && !s.equals("https")) return Optional.empty();
      String host = uri.getHost();
      if (host == null || host.isBlank()) return Optional.empty();
      String h = host.toLowerCase(Locale.ROOT);
      for (Provider p : values()) {
        if (p.serves(h, uri)) return Optional.of(p);
      }
      return Optional.empty();
    }

    private boolean serves(String host, URI uri) {
      if (hosts.contains(host)) return true;
      if (subdomainOf == null || !host.endsWith(subdomainOf)) return false;
      String path = uri.normalize().getRawPath();
      return path.startsWith(subdomainPath) && !path.contains("%");
    }
  }
}
