package com.example.short_link.profile.contact;

import com.example.short_link.profile.application.InvalidUsernameException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * CONTACT_CARD block payload — the digital-business-card vertical's core. Stored as JSON in {@code
 * profile_block.content}. Only {@code name} is required; the rest fall through to "not shown" on
 * the rendered card. vCard serialization for the .vcf download happens on the front end, where the
 * browser can offer a save dialog without a round-trip.
 */
public record ContactCard(
    String name,
    String title,
    String company,
    String email,
    String phone,
    String address,
    String website) {

  private static final int NAME_MAX = 60;
  private static final int TITLE_MAX = 80;
  private static final int COMPANY_MAX = 80;
  private static final int EMAIL_MAX = 254;
  private static final int PHONE_MAX = 30;
  private static final int ADDRESS_MAX = 200;
  private static final int WEBSITE_MAX = 256;

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidUsernameException("contact card: config required");
    }
    ContactCard parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), ContactCard.class);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("contact card: malformed json");
    }
    String name = trimTo(parsed.name, NAME_MAX);
    if (name == null || name.isEmpty()) {
      throw new InvalidUsernameException("contact card: name required");
    }
    String email = trimTo(parsed.email, EMAIL_MAX);
    if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
      throw new InvalidUsernameException("contact card: email malformed");
    }
    String website = trimTo(parsed.website, WEBSITE_MAX);
    if (website != null) validateHttpUrl(website);
    ContactCard out =
        new ContactCard(
            name,
            trimTo(parsed.title, TITLE_MAX),
            trimTo(parsed.company, COMPANY_MAX),
            email,
            trimTo(parsed.phone, PHONE_MAX),
            trimTo(parsed.address, ADDRESS_MAX),
            website);
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("contact card: serialization failed");
    }
  }

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }

  private static void validateHttpUrl(String url) {
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (scheme == null
          || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
        throw new InvalidUsernameException("contact card: website must be http(s)");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new InvalidUsernameException("contact card: website missing host");
      }
    } catch (IllegalArgumentException ex) {
      throw new InvalidUsernameException("contact card: website malformed");
    }
  }
}
