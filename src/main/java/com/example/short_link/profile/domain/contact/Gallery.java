package com.example.short_link.profile.domain.contact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;

public final class Gallery {

  public static final int MAX_IMAGES = 6;

  private static final int URL_MAX = 256;

  private static final TypeReference<GalleryPayload> PAYLOAD_TYPE = new TypeReference<>() {};

  private Gallery() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GalleryPayload(List<String> images) {}

  public static String normalize(String raw) {
    GalleryPayload parsed = BlockConfig.read(raw, PAYLOAD_TYPE, "gallery");
    List<String> images = images(parsed);
    List<String> out = new ArrayList<>(images.size());
    for (String url : images) {
      String kept = imageUrl(url);
      if (kept != null) {
        out.add(kept);
      }
    }
    if (out.isEmpty()) {
      throw BlockConfig.invalid("gallery: at least 1 image required");
    }
    return BlockConfig.write(new GalleryPayload(out), "gallery");
  }

  private static List<String> images(GalleryPayload parsed) {
    List<String> images = parsed == null ? null : parsed.images;
    if (images == null || images.isEmpty()) {
      throw BlockConfig.invalid("gallery: at least 1 image required");
    }
    if (images.size() > MAX_IMAGES) {
      throw BlockConfig.invalid("gallery: max " + MAX_IMAGES + " images");
    }
    return images;
  }

  private static String imageUrl(String url) {
    String trimmed = url == null ? "" : url.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > URL_MAX) {
      throw BlockConfig.invalid("gallery: url too long");
    }
    BlockConfig.requireHttpUrl(trimmed, "gallery: url");
    return trimmed;
  }
}
