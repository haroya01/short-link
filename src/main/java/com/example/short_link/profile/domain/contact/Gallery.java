package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * GALLERY block payload — ordered list of image URLs rendered as a horizontal swipe carousel on the
 * public profile. Capped at {@link #MAX_IMAGES} to keep the JSON inside the 2048-char content
 * column with headroom + so a single block doesn't dominate the visitor's scroll.
 */
public final class Gallery {

  /** Cap chosen so 6 * 256-char URLs + JSON overhead fit comfortably in VARCHAR(2048). */
  public static final int MAX_IMAGES = 6;

  private static final int URL_MAX = 256;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final TypeReference<GalleryPayload> PAYLOAD_TYPE = new TypeReference<>() {};

  private Gallery() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GalleryPayload(List<String> images) {}

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidUsernameException("gallery: config required");
    }
    GalleryPayload parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), PAYLOAD_TYPE);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("gallery: malformed json");
    }
    List<String> images = parsed == null ? null : parsed.images;
    if (images == null || images.isEmpty()) {
      throw new InvalidUsernameException("gallery: at least 1 image required");
    }
    if (images.size() > MAX_IMAGES) {
      throw new InvalidUsernameException("gallery: max " + MAX_IMAGES + " images");
    }
    List<String> out = new ArrayList<>(images.size());
    for (String url : images) {
      if (url == null) continue;
      String trimmed = url.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.length() > URL_MAX) {
        throw new InvalidUsernameException("gallery: url too long");
      }
      validateImageUrl(trimmed);
      out.add(trimmed);
    }
    if (out.isEmpty()) {
      throw new InvalidUsernameException("gallery: at least 1 image required");
    }
    try {
      return MAPPER.writeValueAsString(new GalleryPayload(out));
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("gallery: serialization failed");
    }
  }

  private static void validateImageUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw new InvalidUsernameException("gallery: url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new InvalidUsernameException("gallery: url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new InvalidUsernameException("gallery: url missing host");
    }
  }
}
