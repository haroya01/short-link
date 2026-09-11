package com.example.short_link.profile.application;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 사용자의 소셜 프로필 URL을 검증해 users.socials의 JSON 배열로 저장한다. */
public final class Socials {

  public static final List<String> ALLOWED =
      List.of("x", "line", "threads", "facebook", "kakao", "instagram", "linkedin");

  private static final Set<String> ALLOWED_SET = Set.copyOf(ALLOWED);

  public static final int MAX = 2;

  private static final int URL_MAX = 256;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final TypeReference<List<Social>> LIST_TYPE = new TypeReference<>() {};

  private Socials() {}

  public record Social(String channel, String url) {}

  /** null·공백은 삭제를 뜻한다. 순서를 유지하며 같은 채널은 첫 값만 남긴다. */
  public static String normalize(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    List<Social> parsed;
    try {
      parsed = MAPPER.readValue(trimmed, LIST_TYPE);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: malformed json");
    }
    if (parsed == null) return null;
    if (parsed.size() > MAX) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "socials: up to " + MAX + " allowed");
    }
    List<Social> out = new ArrayList<>(parsed.size());
    Set<String> seen = new HashSet<>();
    for (Social s : parsed) {
      if (s == null) continue;
      String channel = s.channel() == null ? "" : s.channel().trim().toLowerCase(Locale.ROOT);
      String url = s.url() == null ? "" : s.url().trim();
      if (channel.isEmpty() || url.isEmpty()) continue;
      if (!ALLOWED_SET.contains(channel)) {
        throw new ProfileException(
            ProfileErrorCode.INVALID_USERNAME, "socials: unknown channel: " + channel);
      }
      if (url.length() > URL_MAX) {
        throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url too long");
      }
      validateUrl(url);
      if (!seen.add(channel)) continue;
      out.add(new Social(channel, url));
    }
    if (out.isEmpty()) return null;
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "socials: serialization failed");
    }
  }

  /** null·공백인 저장 값은 빈 목록으로 반환한다. */
  public static List<Social> toList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      List<Social> parsed = MAPPER.readValue(json, LIST_TYPE);
      return parsed == null ? List.of() : parsed;
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }

  private static void validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url missing host");
    }
  }
}
