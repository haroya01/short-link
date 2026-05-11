package com.example.short_link.profile.oembed;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Whitelist of oembed providers we proxy. Acts as the SSRF guard: a user-supplied URL only resolves
 * to a provider if its host matches one of these entries — anything else (private IPs, arbitrary
 * domains) is rejected before we ever make an outbound call. Add a new provider by appending an
 * enum entry, not by widening the host match.
 */
public enum EmbedProvider {
  YOUTUBE(
      "youtube",
      "https://www.youtube.com/oembed",
      Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")),
  VIMEO(
      "vimeo",
      "https://vimeo.com/api/oembed.json",
      Set.of("vimeo.com", "www.vimeo.com", "player.vimeo.com")),
  SPOTIFY("spotify", "https://open.spotify.com/oembed", Set.of("open.spotify.com")),
  SOUNDCLOUD(
      "soundcloud",
      "https://soundcloud.com/oembed",
      Set.of("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com"));

  private static final List<EmbedProvider> ALL = List.of(values());

  private final String id;
  private final String endpoint;
  private final Set<String> hosts;

  EmbedProvider(String id, String endpoint, Set<String> hosts) {
    this.id = id;
    this.endpoint = endpoint;
    this.hosts = hosts;
  }

  public String id() {
    return id;
  }

  public String endpoint() {
    return endpoint;
  }

  public static Optional<EmbedProvider> resolve(String url) {
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
    for (EmbedProvider p : ALL) {
      if (p.hosts.contains(h)) return Optional.of(p);
    }
    return Optional.empty();
  }
}
