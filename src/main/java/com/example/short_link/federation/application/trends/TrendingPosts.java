package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// What other servers say is trending, asked of each configured server in turn and held only in
// memory: a refresh replaces the whole list, so a post deleted there is gone from here by the next
// one. Each server's own order is kept: every server's first post comes before any server's
// second, and posts at the same place go by how many servers carry them, then by boosts and likes.
// A server that does not answer is left out and asked again after 1, 2, 4 and at most 8 skipped
// refreshes. Nothing from a server this one blocked is taken, and a member never sees an account
// or server they blocked or muted.
@Slf4j
@Service
public class TrendingPosts {

  static final int MAX_LIMIT = 40;
  private static final int MAX_FAILURES = 4;

  private final TrendsProperties props;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final FederationDomainBlockRepository serverBlocks;
  private final RemoteAccountStandingReader standings;
  private final TrendingStatusReader reader;
  private final JsonMapper json;
  private final Clock clock;
  private final Map<String, Backoff> backoffs = new HashMap<>();

  private volatile List<TrendingStatusReader.Read> trending = List.of();

  private record Backoff(int failures, int skipsLeft) {}

  @Autowired
  public TrendingPosts(
      TrendsProperties props,
      FederationHttp http,
      SigningKeys signingKeys,
      FederationDomainBlockRepository serverBlocks,
      RemoteAccountStandingReader standings,
      TrendingStatusReader reader,
      JsonMapper json) {
    this(props, http, signingKeys, serverBlocks, standings, reader, json, Clock.systemUTC());
  }

  TrendingPosts(
      TrendsProperties props,
      FederationHttp http,
      SigningKeys signingKeys,
      FederationDomainBlockRepository serverBlocks,
      RemoteAccountStandingReader standings,
      TrendingStatusReader reader,
      JsonMapper json,
      Clock clock) {
    this.props = props;
    this.http = http;
    this.signingKeys = signingKeys;
    this.serverBlocks = serverBlocks;
    this.standings = standings;
    this.reader = reader;
    this.json = json;
    this.clock = clock;
  }

  public List<TrendingNote> list(Long viewerId, int limit) {
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    List<TrendingStatusReader.Read> all = trending;
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
        .limit(size)
        .map(TrendingStatusReader.Read::note)
        .toList();
  }

  public void refresh() {
    Map<String, Boolean> blocked = new HashMap<>();
    Signer signer = null;
    List<List<TrendingStatusReader.Read>> answers = new ArrayList<>();
    for (String server : props.hosts()) {
      if (waiting(server) || blocked(server, blocked)) {
        continue;
      }
      if (signer == null) {
        signer = signingKeys.forInstance();
      }
      Optional<List<TrendingStatusReader.Read>> answer = ask(server, signer);
      if (answer.isEmpty()) {
        failed(server);
        continue;
      }
      backoffs.remove(server);
      answers.add(answer.get().stream().filter(read -> !blocked(read.domain(), blocked)).toList());
    }
    trending = blend(answers);
  }

  // A server blocked here leaves at once, without waiting for the next refresh.
  public void withdraw(String domain) {
    trending =
        trending.stream()
            .filter(read -> !read.domain().equals(domain) && !read.domain().endsWith("." + domain))
            .toList();
  }

  private Optional<List<TrendingStatusReader.Read>> ask(String server, Signer signer) {
    URI uri = URI.create("https://" + server + "/api/v1/trends/statuses?limit=" + props.perHost());
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
    List<TrendingStatusReader.Read> reads = new ArrayList<>();
    for (JsonNode status : statuses) {
      if (status.isObject()) {
        reader.read(status, server).ifPresent(reads::add);
      }
      if (reads.size() == props.perHost()) {
        break;
      }
    }
    return Optional.of(reads);
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

  private boolean blocked(String domain, Map<String, Boolean> blocked) {
    return blocked.computeIfAbsent(domain, host -> serverBlocks.severityFor(host).isPresent());
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
          first.domain());
    }
  }
}
