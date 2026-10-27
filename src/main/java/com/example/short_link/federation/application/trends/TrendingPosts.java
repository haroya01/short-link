package com.example.short_link.federation.application.trends;

import com.example.short_link.common.discovery.TrendingSpread;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class TrendingPosts {

  static final int MAX_LIMIT = 40;
  private static final int MAX_FAILURES = 4;

  private final TrendsProperties props;
  private final TrendingSpread spread;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final FederationDomainBlockRepository serverBlocks;
  private final RemoteAccountStandingReader standings;
  private final TrendingStatusReader reader;
  private final FederationUrls urls;
  private final RemoteReach reach;
  private final JsonMapper json;
  private final Clock clock;
  private final Map<String, Backoff> backoffs = new HashMap<>();

  private volatile List<TrendingStatusReader.Read> trending = List.of();

  private record Backoff(int failures, int skipsLeft) {}

  public TrendingPosts(
      TrendsProperties props,
      TrendingSpread spread,
      FederationHttp http,
      SigningKeys signingKeys,
      FederationDomainBlockRepository serverBlocks,
      RemoteAccountStandingReader standings,
      TrendingStatusReader reader,
      FederationUrls urls,
      RemoteReach reach,
      JsonMapper json,
      Clock clock) {
    this.props = props;
    this.spread = spread;
    this.http = http;
    this.signingKeys = signingKeys;
    this.serverBlocks = serverBlocks;
    this.standings = standings;
    this.reader = reader;
    this.urls = urls;
    this.reach = reach;
    this.json = json;
    this.clock = clock;
  }

  public record Page(List<TrendingNote> items, boolean more, List<String> unknownAuthors) {}

  public List<TrendingNote> list(Long viewerId, int limit) {
    return page(viewerId, 0, limit).items();
  }

  public Page page(Long viewerId, int offset, int limit) {
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    List<TrendingStatusReader.Read> shown = inRounds(shown(viewerId, trending));
    int from = Math.clamp(offset, 0, shown.size());
    List<TrendingStatusReader.Read> page = shown.subList(from, Math.min(from + size, shown.size()));
    return new Page(
        page.stream().map(read -> read.note().seenBy(viewerId != null)).toList(),
        from + page.size() < shown.size(),
        page.stream()
            .filter(read -> read.note().author().remoteId() == null)
            .map(TrendingStatusReader.Read::actorUri)
            .distinct()
            .toList());
  }

  List<TrendingStatusReader.Read> pool(Long viewerId) {
    return shown(viewerId, trending);
  }

  public Optional<JsonNode> object(String address) {
    return trending.stream()
        .filter(read -> address.equals(read.note().uri()) || address.equals(read.note().url()))
        .map(TrendingStatusReader.Read::object)
        .findFirst();
  }

  private List<TrendingStatusReader.Read> shown(
      Long viewerId, List<TrendingStatusReader.Read> all) {
    RemoteAccountStandingReader.Hidden hidden =
        viewerId == null || all.isEmpty()
            ? RemoteAccountStandingReader.Hidden.NONE
            : standings.hidden(
                viewerId,
                all.stream().map(TrendingStatusReader.Read::actorUri).distinct().toList(),
                all.stream().map(TrendingStatusReader.Read::domain).distinct().toList(),
                clock.instant());
    return all.stream()
        .filter(
            read ->
                !hidden.actorUris().contains(read.actorUri())
                    && !hidden.domains().contains(read.domain()))
        .toList();
  }

  private List<TrendingStatusReader.Read> inRounds(List<TrendingStatusReader.Read> reads) {
    Map<String, Integer> placed = new HashMap<>();
    List<List<TrendingStatusReader.Read>> rounds = new ArrayList<>();
    for (TrendingStatusReader.Read read : reads) {
      int round = (placed.merge(read.actorUri(), 1, Integer::sum) - 1) / spread.perAuthor();
      while (rounds.size() <= round) {
        rounds.add(new ArrayList<>());
      }
      rounds.get(round).add(read);
    }
    return rounds.stream().flatMap(List::stream).toList();
  }

  public void refresh() {
    BlockedHosts blocked = new BlockedHosts(serverBlocks);
    Signer signer = null;
    List<List<TrendingStatusReader.Read>> answers = new ArrayList<>();
    Set<String> keepable = new HashSet<>();
    for (String server : props.hosts()) {
      if (waiting(server) || blocked.contains(server)) {
        continue;
      }
      if (signer == null) {
        signer = signingKeys.forInstance();
      }
      Optional<List<TrendingStatusReader.Read>> answer = ask(server, signer, keepable);
      if (answer.isEmpty()) {
        failed(server);
        continue;
      }
      backoffs.remove(server);
      answers.add(answer.get().stream().filter(read -> !blocked.contains(read.domain())).toList());
    }
    // A refresh replaces the whole list, so a post deleted on its server is gone by the next one.
    trending = known(blend(answers));
    reach.trended(trending, keepable);
  }

  private List<TrendingStatusReader.Read> known(List<TrendingStatusReader.Read> reads) {
    if (reads.isEmpty()) {
      return reads;
    }
    Map<String, Long> ids =
        standings.idsOf(
            reads.stream().map(TrendingStatusReader.Read::actorUri).distinct().toList());
    return reads.stream()
        .map(
            read ->
                ids.containsKey(read.actorUri())
                    ? new TrendingStatusReader.Read(
                        read.note().by(read.note().author().known(ids.get(read.actorUri()))),
                        read.actorUri(),
                        read.domain(),
                        read.object(),
                        read.quoteAccepted())
                    : read)
        .toList();
  }

  public void withdraw(String domain) {
    trending =
        trending.stream()
            .filter(read -> !read.domain().equals(domain) && !read.domain().endsWith("." + domain))
            .toList();
  }

  // Mastodon answers at most 40 a request; the next ones come by offset. A short page is the last.
  private Optional<List<TrendingStatusReader.Read>> ask(
      String server, Signer signer, Set<String> keepable) {
    List<TrendingStatusReader.Read> reads = new ArrayList<>();
    for (int page = 0; page < props.pages(); page++) {
      Optional<JsonNode> statuses = askPage(server, signer, page * props.perHost());
      if (statuses.isEmpty()) {
        return page == 0 ? Optional.empty() : Optional.of(reads);
      }
      for (JsonNode status : statuses.get()) {
        if (status.isObject()) {
          reader
              .read(status, server)
              .ifPresent(
                  read -> {
                    reads.add(read);
                    if (LatestPosts.keepable(status, urls)) {
                      keepable.add(read.note().uri());
                    }
                  });
        }
      }
      if (statuses.get().size() < props.perHost()) {
        break;
      }
    }
    return Optional.of(reads);
  }

  private Optional<JsonNode> askPage(String server, Signer signer, int offset) {
    URI uri =
        URI.create(
            "https://"
                + server
                + "/api/v1/trends/statuses?limit="
                + props.perHost()
                + (offset == 0 ? "" : "&offset=" + offset));
    FederationHttp.Result result = http.getJson(uri, signer, props.patience());
    if (!(result instanceof FederationHttp.Result.Ok ok)) {
      log.info("federation trends unanswered host={} outcome={}", server, result);
      return Optional.empty();
    }
    JsonNode statuses;
    try {
      statuses = json.readTree(ok.body());
    } catch (JacksonException malformed) {
      log.info("federation trends malformed host={}", server);
      return Optional.empty();
    }
    if (statuses == null || !statuses.isArray()) {
      log.info("federation trends malformed host={}", server);
      return Optional.empty();
    }
    return Optional.of(statuses);
  }

  private boolean waiting(String server) {
    Backoff backoff = backoffs.get(server);
    if (backoff == null || backoff.skipsLeft() == 0) {
      return false;
    }
    backoffs.put(server, new Backoff(backoff.failures(), backoff.skipsLeft() - 1));
    return true;
  }

  private void failed(String server) {
    int failures =
        Math.min(backoffs.getOrDefault(server, new Backoff(0, 0)).failures() + 1, MAX_FAILURES);
    backoffs.put(server, new Backoff(failures, 1 << (failures - 1)));
  }

  static List<TrendingStatusReader.Read> blend(List<List<TrendingStatusReader.Read>> answers) {
    Map<String, Merged> byUri = new LinkedHashMap<>();
    for (List<TrendingStatusReader.Read> answer : answers) {
      for (int place = 0; place < answer.size(); place++) {
        TrendingStatusReader.Read read = answer.get(place);
        Merged kept = byUri.get(read.note().uri());
        if (kept == null) {
          byUri.put(read.note().uri(), new Merged(read, place));
        } else {
          kept.alsoAt(read, place);
        }
      }
    }
    return byUri.values().stream()
        .sorted(
            Comparator.comparingInt(Merged::place)
                .thenComparing(Comparator.comparingInt((Merged m) -> m.servers.size()).reversed())
                .thenComparing(Comparator.comparingLong(Merged::reach).reversed()))
        .map(Merged::read)
        .toList();
  }

  private static final class Merged {

    private final TrendingStatusReader.Read first;
    private final Set<String> servers = new LinkedHashSet<>();
    private int place;
    private long likes;
    private long reposts;
    private long replies;

    Merged(TrendingStatusReader.Read read, int place) {
      this.first = read;
      this.place = place;
      add(read);
    }

    void alsoAt(TrendingStatusReader.Read read, int at) {
      place = Math.min(place, at);
      add(read);
    }

    private void add(TrendingStatusReader.Read read) {
      servers.addAll(read.note().servers());
      likes = Math.max(likes, read.note().likeCount());
      reposts = Math.max(reposts, read.note().repostCount());
      replies = Math.max(replies, read.note().replyCount());
    }

    int place() {
      return place;
    }

    long reach() {
      return likes + reposts;
    }

    TrendingStatusReader.Read read() {
      return new TrendingStatusReader.Read(
          first.note().on(List.copyOf(servers), likes, reposts, replies),
          first.actorUri(),
          first.domain(),
          first.object(),
          first.quoteAccepted());
    }
  }
}
