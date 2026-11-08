package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class LatestPosts {

  private final LatestProperties props;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final FederationDomainBlockRepository serverBlocks;
  private final RemoteAccountStandingReader standings;
  private final TrendingStatusReader reader;
  private final LatestStore store;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final Clock clock;
  private final ExecutorService askers = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicBoolean refreshing = new AtomicBoolean();
  private final Map<String, Edge> edges = new ConcurrentHashMap<>();
  private final HostRest rests;

  private volatile List<Kept> latest = List.of();
  private volatile Instant askedAt;

  record Kept(TrendingStatusReader.Read read, String host) {}

  record Edge(String newest, String oldest, boolean bottom) {

    static Edge firstAnswer(String first, String last, boolean shortPage) {
      return first == null ? new Edge("0", "0", true) : new Edge(first, last, shortPage);
    }

    Edge afterNewer(String first) {
      return first == null ? this : new Edge(first, oldest, bottom);
    }

    Edge afterOlder(String last, boolean shortPage) {
      return new Edge(newest, last == null ? oldest : last, shortPage);
    }
  }

  private record Answer(
      String host,
      List<TrendingStatusReader.Read> reads,
      String first,
      String last,
      Instant lastAt,
      int raw) {}

  public record Page(List<TrendingNote> items, boolean more, List<String> unknownAuthors) {}

  public LatestPosts(
      LatestProperties props,
      FederationHttp http,
      SigningKeys signingKeys,
      FederationDomainBlockRepository serverBlocks,
      RemoteAccountStandingReader standings,
      TrendingStatusReader reader,
      LatestStore store,
      FederationUrls urls,
      JsonMapper json,
      Clock clock) {
    this.props = props;
    this.http = http;
    this.signingKeys = signingKeys;
    this.serverBlocks = serverBlocks;
    this.standings = standings;
    this.reader = reader;
    this.store = store;
    this.urls = urls;
    this.json = json;
    this.clock = clock;
    this.rests = new HostRest(clock);
  }

  public Page page(Long viewerId, int offset, int limit) {
    if (!props.enabled()) {
      return new Page(List.of(), false, List.of());
    }
    fresh();
    int size = Math.clamp(limit, 1, TrendingPosts.MAX_LIMIT);
    if (offset + size > latest.size()) {
      deepen();
    }
    List<TrendingStatusReader.Read> shown = shown(viewerId);
    int from = Math.clamp(offset, 0, shown.size());
    List<TrendingStatusReader.Read> page = shown.subList(from, Math.min(from + size, shown.size()));
    boolean more = from + page.size() < shown.size() || canDeepen();
    return new Page(
        page.stream().map(read -> read.note().seenBy(viewerId != null)).toList(),
        more,
        page.stream()
            .filter(read -> read.note().author().remoteId() == null)
            .map(TrendingStatusReader.Read::actorUri)
            .distinct()
            .toList());
  }

  List<TrendingStatusReader.Read> pool(Long viewerId) {
    if (!props.enabled()) {
      return List.of();
    }
    fresh();
    return shown(viewerId);
  }

  // Other servers are asked only while someone reads.
  private void fresh() {
    if (askedAt == null) {
      refresh();
    } else if (askedAt.plus(props.freshFor()).isBefore(clock.instant())) {
      CompletableFuture.runAsync(this::refresh, askers)
          .exceptionally(
              failed -> {
                log.warn("federation latest refresh failed reason={}", failed.toString());
                return null;
              });
    }
  }

  private List<TrendingStatusReader.Read> shown(Long viewerId) {
    List<Kept> all = latest;
    RemoteAccountStandingReader.Hidden hidden =
        viewerId == null || all.isEmpty()
            ? RemoteAccountStandingReader.Hidden.NONE
            : standings.hidden(
                viewerId,
                all.stream().map(kept -> kept.read().actorUri()).distinct().toList(),
                all.stream().map(kept -> kept.read().domain()).distinct().toList(),
                clock.instant());
    return all.stream()
        .map(Kept::read)
        .filter(
            read ->
                !hidden.actorUris().contains(read.actorUri())
                    && !hidden.domains().contains(read.domain()))
        .toList();
  }

  public Optional<JsonNode> object(String address) {
    return latest.stream()
        .map(Kept::read)
        .filter(read -> address.equals(read.note().uri()) || address.equals(read.note().url()))
        .map(TrendingStatusReader.Read::object)
        .findFirst();
  }

  void refresh() {
    if (!refreshing.compareAndSet(false, true)) {
      return;
    }
    try {
      askedAt = clock.instant();
      keep(askAll(edge -> edge == null ? "" : "&since_id=" + edge.newest()), true);
    } finally {
      refreshing.set(false);
    }
  }

  public int pull() {
    if (!props.pulling() || !refreshing.compareAndSet(false, true)) {
      return 0;
    }
    try {
      List<Kept> newest =
          pulled().stream()
              .sorted(
                  Comparator.comparing((Kept kept) -> kept.read().note().createdAt()).reversed())
              .limit(store.leftToday())
              .toList();
      int stored = 0;
      for (Kept kept : newest.reversed()) {
        if (LatestStore.PULLED.equals(store.keep(kept.host(), kept.read()))) {
          stored++;
        }
      }
      return stored;
    } finally {
      refreshing.set(false);
    }
  }

  private Collection<Kept> pulled() {
    Signer signer = signingKeys.forInstance();
    BlockedHosts blocked = new BlockedHosts(serverBlocks);
    List<CompletableFuture<List<Kept>>> asks = new ArrayList<>();
    for (String host : props.hosts()) {
      if (!rests.resting(host, clock.instant()) && !blocked.contains(host)) {
        asks.add(CompletableFuture.supplyAsync(() -> pullFrom(host, signer), askers));
      }
    }
    Map<String, Kept> byUri = new LinkedHashMap<>();
    for (CompletableFuture<List<Kept>> ask : asks) {
      for (Kept kept : ask.join()) {
        if (!blocked.contains(kept.read().domain())) {
          byUri.putIfAbsent(kept.read().note().uri(), kept);
        }
      }
    }
    return byUri.values();
  }

  private List<Kept> pullFrom(String host, Signer signer) {
    Edge edge = edges.get(host);
    if (edge == null) {
      return backfill(host, signer);
    }
    Optional<Answer> answer = ask(host, "&since_id=" + edge.newest(), signer);
    answer.ifPresent(newer -> moveEdge(newer, true));
    return answer.map(LatestPosts::kept).orElse(List.of());
  }

  private List<Kept> backfill(String host, Signer signer) {
    Instant reached = store.newest(host).orElse(null);
    List<Kept> pulled = new ArrayList<>();
    String window = "";
    for (int page = 0; page < props.backfillPages(); page++) {
      Optional<Answer> answer = ask(host, window, signer);
      if (answer.isEmpty() || answer.get().first() == null) {
        break;
      }
      Answer older = answer.get();
      pulled.addAll(kept(older));
      moveEdge(older, false);
      if (older.raw() < props.perHost() || reaches(older, reached)) {
        break;
      }
      window = "&max_id=" + older.last();
    }
    return pulled;
  }

  private static boolean reaches(Answer older, Instant reached) {
    return reached != null && older.lastAt() != null && !older.lastAt().isAfter(reached);
  }

  private void moveEdge(Answer answer, boolean newer) {
    Edge edge = edges.get(answer.host());
    boolean shortPage = answer.raw() < props.perHost();
    if (edge == null) {
      edges.put(answer.host(), Edge.firstAnswer(answer.first(), answer.last(), shortPage));
    } else if (newer) {
      edges.put(answer.host(), edge.afterNewer(answer.first()));
    } else {
      edges.put(answer.host(), edge.afterOlder(answer.last(), shortPage));
    }
  }

  private static List<Kept> kept(Answer answer) {
    return answer.reads().stream().map(read -> new Kept(read, answer.host())).toList();
  }

  private boolean canDeepen() {
    return latest.size() < props.maxItems()
        && props.hosts().stream()
            .anyMatch(
                host -> {
                  Edge edge = edges.get(host);
                  return edge != null && !edge.bottom();
                });
  }

  private void deepen() {
    if (!canDeepen() || !refreshing.compareAndSet(false, true)) {
      return;
    }
    try {
      keep(
          askAll(edge -> edge == null || edge.bottom() ? null : "&max_id=" + edge.oldest()), false);
    } finally {
      refreshing.set(false);
    }
  }

  private List<Answer> askAll(Function<Edge, String> window) {
    Signer signer = signingKeys.forInstance();
    BlockedHosts blocked = new BlockedHosts(serverBlocks);
    List<CompletableFuture<Optional<Answer>>> asks = new ArrayList<>();
    for (String host : props.hosts()) {
      String query = window.apply(edges.get(host));
      if (query == null || rests.resting(host, clock.instant()) || blocked.contains(host)) {
        continue;
      }
      asks.add(CompletableFuture.supplyAsync(() -> ask(host, query, signer), askers));
    }
    List<Answer> answers = new ArrayList<>();
    for (CompletableFuture<Optional<Answer>> ask : asks) {
      ask.join().ifPresent(answers::add);
    }
    return answers;
  }

  private Optional<Answer> ask(String host, String query, Signer signer) {
    Optional<JsonNode> statuses = timeline(host, query, signer);
    if (statuses.isEmpty()) {
      return Optional.empty();
    }
    Answer answer = answer(host, statuses.get());
    if (statuses.get().isEmpty() && !edges.containsKey(host)) {
      rest(host);
      return Optional.empty();
    }
    rests.answered(host);
    return Optional.of(answer);
  }

  private Optional<JsonNode> timeline(String host, String query, Signer signer) {
    URI uri =
        URI.create(
            "https://"
                + host
                + "/api/v1/timelines/public?local=true&limit="
                + props.perHost()
                + query);
    FederationHttp.Result result = http.getJson(uri, signer, props.patience());
    if (result instanceof FederationHttp.Result.Failed failed
        && failed.status() >= 400
        && failed.status() < 500) {
      rest(host);
      return Optional.empty();
    }
    if (!(result instanceof FederationHttp.Result.Ok ok)) {
      log.info("federation latest unanswered host={} outcome={}", host, result);
      return Optional.empty();
    }
    JsonNode statuses;
    try {
      statuses = json.readTree(ok.body());
    } catch (JacksonException malformed) {
      return Optional.empty();
    }
    return statuses != null && statuses.isArray() ? Optional.of(statuses) : Optional.empty();
  }

  private Answer answer(String host, JsonNode statuses) {
    List<TrendingStatusReader.Read> reads = new ArrayList<>();
    String first = null;
    String last = null;
    Instant lastAt = null;
    for (JsonNode status : statuses) {
      String id = status.path("id").asString(null);
      if (id == null) {
        continue;
      }
      first = first == null ? id : first;
      last = id;
      lastAt = PublicStatuses.createdAt(status);
      // A pulled post naming a member is left to its server's delivery to them, which would
      // otherwise find it kept and drop it as a duplicate.
      if (PublicStatuses.safe(status)
          && !(props.pulling() && PublicStatuses.namesUs(status, urls))) {
        reader.read(status, host).ifPresent(reads::add);
      }
    }
    return new Answer(host, reads, first, last, lastAt, statuses.size());
  }

  // A server that keeps its public timeline from visitors answers 4xx, or 200 with nothing at all
  // on the first ask.
  private void rest(String host) {
    log.info("federation latest no preview host={} wait={}", host, rests.rest(host));
  }

  private void keep(List<Answer> answers, boolean newer) {
    BlockedHosts blocked = new BlockedHosts(serverBlocks);
    Map<String, Kept> byUri = new LinkedHashMap<>();
    for (Kept kept : latest) {
      byUri.put(kept.read().note().uri(), kept);
    }
    for (Answer answer : answers) {
      moveEdge(answer, newer);
      for (TrendingStatusReader.Read read : answer.reads()) {
        if (!blocked.contains(read.domain())) {
          byUri.putIfAbsent(read.note().uri(), new Kept(read, answer.host()));
        }
      }
    }
    latest =
        byUri.values().stream()
            .sorted(Comparator.comparing((Kept kept) -> kept.read().note().createdAt()).reversed())
            .limit(props.maxItems())
            .toList();
  }

  public void withdraw(String domain) {
    latest =
        latest.stream()
            .filter(
                kept ->
                    !kept.read().domain().equals(domain)
                        && !kept.read().domain().endsWith("." + domain))
            .toList();
  }
}
