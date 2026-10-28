package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.repository.RemoteDomainReader;
import com.example.short_link.federation.domain.repository.RemoteDomainReader.KnownDomain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnownRemoteDomains {

  public static final String CACHE = "federation-known-domains";

  static final int CAP = 1_000;

  static final List<String> SEEDS =
      List.of(
          "mastodon.social",
          "misskey.io",
          "mstdn.jp",
          "fedibird.com",
          "qdon.space",
          "planet.moe",
          "social.silicon.moe",
          "mastodon.online",
          "mas.to",
          "fosstodon.org");

  private final RemoteDomainReader reader;
  private final FederationUrls urls;
  private final List<String> named;

  public KnownRemoteDomains(
      RemoteDomainReader reader,
      FederationUrls urls,
      @Value("${short-link.federation.trends.hosts:}") List<String> trendHosts) {
    this.reader = reader;
    this.urls = urls;
    Set<String> names = new LinkedHashSet<>(SEEDS);
    for (String host : trendHosts) {
      String name = host.strip().toLowerCase(Locale.ROOT);
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    this.named = List.copyOf(names);
  }

  public record Ranked(List<String> domains) {

    public Ranked {
      domains = domains == null ? List.of() : List.copyOf(domains);
    }
  }

  @Cacheable(value = CACHE, key = "'ranked'")
  @Transactional(readOnly = true)
  public Ranked ranked() {
    List<KnownDomain> known = new ArrayList<>(reader.known(named, CAP));
    known.removeIf(domain -> domain.domain().equals(urls.domain()));
    known.sort(
        Comparator.comparingLong(KnownDomain::accounts)
            .reversed()
            .thenComparingInt(this::namedAt)
            .thenComparing(KnownDomain::domain));
    return new Ranked(known.stream().map(KnownDomain::domain).toList());
  }

  private int namedAt(KnownDomain domain) {
    int at = named.indexOf(domain.domain());
    return at < 0 ? named.size() : at;
  }
}
