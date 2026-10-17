package com.example.short_link.federation.application;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// What completes the server part of a handle as it is typed: "@gargron@m", "gargron@m" and "m" all
// ask for servers starting with "m"; nothing typed yet asks for the best known ones.
@Service
@RequiredArgsConstructor
public class RemoteDomainSuggestions {

  static final int MAX_LIMIT = 10;
  private static final Pattern TYPED = Pattern.compile("[a-z0-9.:-]{0,253}");

  private final KnownRemoteDomains domains;

  public List<String> suggest(String typed, int limit) {
    String raw = typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
    String prefix = raw.substring(raw.lastIndexOf('@') + 1);
    if (!TYPED.matcher(prefix).matches()) {
      return List.of();
    }
    return domains.ranked().domains().stream()
        .filter(domain -> domain.startsWith(prefix))
        .limit(Math.clamp(limit, 1, MAX_LIMIT))
        .toList();
  }
}
