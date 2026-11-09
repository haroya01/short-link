package com.example.short_link.profile.application;

import com.example.short_link.profile.domain.contact.BlockConfig;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

  public static String normalize(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    List<Social> parsed = parse(trimmed);
    if (parsed == null) return null;
    if (parsed.size() > MAX) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "socials: up to " + MAX + " allowed");
    }
    List<Social> out = kept(parsed);
    return out.isEmpty() ? null : BlockConfig.write(out, "socials");
  }

  private static List<Social> parse(String trimmed) {
    try {
      return MAPPER.readValue(trimmed, LIST_TYPE);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: malformed json");
    }
  }

  private static List<Social> kept(List<Social> parsed) {
    List<Social> out = new ArrayList<>(parsed.size());
    Set<String> seen = new HashSet<>();
    for (Social s : parsed) {
      Social kept = social(s);
      if (kept != null && seen.add(kept.channel())) {
        out.add(kept);
      }
    }
    return out;
  }

  private static Social social(Social s) {
    if (s == null) return null;
    String channel = s.channel() == null ? "" : s.channel().trim().toLowerCase(Locale.ROOT);
    String url = s.url() == null ? "" : s.url().trim();
    if (channel.isEmpty() || url.isEmpty()) return null;
    requireAllowed(channel, url);
    return new Social(channel, url);
  }

  private static void requireAllowed(String channel, String url) {
    if (!ALLOWED_SET.contains(channel)) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "socials: unknown channel: " + channel);
    }
    if (url.length() > URL_MAX) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "socials: url too long");
    }
    BlockConfig.requireHttpUrl(url, "socials: url");
  }

  public static List<Social> toList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      List<Social> parsed = MAPPER.readValue(json, LIST_TYPE);
      return parsed == null ? List.of() : parsed;
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }
}
