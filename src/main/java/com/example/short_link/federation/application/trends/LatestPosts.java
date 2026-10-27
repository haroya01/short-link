package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

// The newest public posts of a few chosen servers, held only in memory and asked for only while
// someone reads them: the first read waits for the servers, a read after freshFor answers from
// memory and asks again behind it, and nobody reading means nothing is asked. Each refresh asks
// only for what came after the newest post kept from that server; scrolling past the oldest asks
// for the page before it. Posts marked sensitive or behind a warning, and posts by bots, are left
// out, as is everything trending leaves out.
//
// Where pulling is on, nothing is held: a worker pulls whether or not anyone reads, and the posts
// are kept as notes. A pulled post that names a member is left to the delivery its server makes
// to them, which would otherwise find it kept and be dropped as a duplicate.
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
  private final Map<String, Quiet> quiet = new ConcurrentHashMap<>();

  private volatile List<Kept> latest = List.of();
  private volatile Instant askedAt;

  record Kept(TrendingStatusReader.Read read, String host) {}

  private record Edge(String newest, String oldest, boolean bottom) {}

  private record Quiet(int times, Instant until) {}

  static final Duration FIRST_QUIET = Duration.ofHours(1);
  static final Duration LONGEST_QUIET = Duration.ofHours(24);

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

  // Each server is asked for what came after the newest post seen from it; the first time, for its
  // newest pages back to the newest post kept from it, at most backfillPages. Past the day's cap
  // the newest posts are the ones kept, and they are kept oldest first, so note ids follow the
  // order they were written in.
  public int pull() {
    if (!props.pulling() || !refreshing.compareAndSet(false, true)) {
      return 0;
    }
    try {
      Signer signer = signingKeys.forInstance();
      Map<String, Boolean> blocked = new HashMap<>();
      List<CompletableFuture<List<Kept>>> asks = new ArrayList<>();
      for (String host : props.hosts()) {
        if (!resting(host) && !blocked(host, blocked)) {
          asks.add(CompletableFuture.supplyAsync(() -> pullFrom(host, signer), askers));
        }
      }
      Map<String, Kept> byUri = new LinkedHashMap<>();
      for (CompletableFuture<List<Kept>> ask : asks) {
        for (Kept kept : ask.join()) {
          if (!blocked(kept.read().domain(), blocked)) {
            byUri.putIfAbsent(kept.read().note().uri(), kept);
          }
        }
      }
      List<Kept> newest =
          byUri.values().stream()
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

  private List<Kept> pullFrom(String host, Signer signer) {
    Edge edge = edges.get(host);
    if (edge != null) {
      Optional<Answer> answer = ask(host, "&since_id=" + edge.newest(), signer);
      answer
          .filter(newer -> newer.first() != null)
          .ifPresent(
              newer -> edges.put(host, new Edge(newer.first(), edge.oldest(), edge.bottom())));
      return answer.map(LatestPosts::kept).orElse(List.of());
    }
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
      boolean bottom = older.raw() < props.perHost();
      Edge before = edges.get(host);
      edges.put(
          host, new Edge(before == null ? older.first() : before.newest(), older.last(), bottom));
      if (bottom
          || (reached != null && older.lastAt() != null && !older.lastAt().isAfter(reached))) {
        break;
      }
      window = "&max_id=" + older.last();
    }
    return pulled;
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
    Map<String, Boolean> blocked = new HashMap<>();
    List<CompletableFuture<Optional<Answer>>> asks = new ArrayList<>();
    for (String host : props.hosts()) {
      String query = window.apply(edges.get(host));
      if (query == null || resting(host) || blocked(host, blocked)) {
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
    if (statuses == null || !statuses.isArray()) {
      return Optional.empty();
    }
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
      lastAt = instant(status.path("created_at").asString(null));
      if (safe(status) && !(props.pulling() && namesUs(status, urls))) {
        reader.read(status, host).ifPresent(reads::add);
      }
    }
    if (statuses.isEmpty() && !edges.containsKey(host)) {
      rest(host);
      return Optional.empty();
    }
    quiet.remove(host);
    return Optional.of(new Answer(host, reads, first, last, lastAt, statuses.size()));
  }

  // A server that keeps its public timeline from visitors answers 4xx, or 200 with nothing at all
  // on the first ask; it is asked again after 1, 2, 4 and at most 24 hours.
  private void rest(String host) {
    Quiet before = quiet.get(host);
    int times = before == null ? 0 : before.times() + 1;
    Duration wait = FIRST_QUIET.multipliedBy(1L << Math.min(times, 5));
    quiet.put(
        host,
        new Quiet(
            times, clock.instant().plus(wait.compareTo(LONGEST_QUIET) > 0 ? LONGEST_QUIET : wait)));
    log.info("federation latest no preview host={} wait={}", host, wait);
  }

  private boolean resting(String host) {
    Quiet rest = quiet.get(host);
    return rest != null && rest.until().isAfter(clock.instant());
  }

  static boolean keepable(JsonNode status, FederationUrls urls) {
    return safe(status) && !namesUs(status, urls);
  }

  private static boolean namesUs(JsonNode status, FederationUrls urls) {
    for (JsonNode mention : status.path("mentions")) {
      String acct = mention.path("acct").asString("");
      int at = acct.lastIndexOf('@');
      String host = at < 0 ? null : RemoteActorParser.host("https://" + acct.substring(at + 1));
      if (urls.isOurs(mention.path("url").asString(null))
          || (host != null && host.equals(urls.domain()))) {
        return true;
      }
    }
    return false;
  }

  private static Instant instant(String at) {
    try {
      return at == null ? null : Instant.parse(at);
    } catch (DateTimeParseException malformed) {
      return null;
    }
  }

  private static boolean safe(JsonNode status) {
    return !status.path("sensitive").asBoolean(false)
        && status.path("spoiler_text").asString("").isBlank()
        && !status.path("account").path("bot").asBoolean(false);
  }

  private void keep(List<Answer> answers, boolean newer) {
    Map<String, Boolean> blocked = new HashMap<>();
    Map<String, Kept> byUri = new LinkedHashMap<>();
    for (Kept kept : latest) {
      byUri.put(kept.read().note().uri(), kept);
    }
    for (Answer answer : answers) {
      Edge edge = edges.get(answer.host());
      boolean shortPage = answer.raw() < props.perHost();
      if (edge == null) {
        edges.put(
            answer.host(),
            answer.first() == null
                ? new Edge("0", "0", true)
                : new Edge(answer.first(), answer.last(), shortPage));
      } else if (newer) {
        if (answer.first() != null) {
          edges.put(answer.host(), new Edge(answer.first(), edge.oldest(), edge.bottom()));
        }
      } else {
        edges.put(
            answer.host(),
            new Edge(
                edge.newest(), answer.last() == null ? edge.oldest() : answer.last(), shortPage));
      }
      for (TrendingStatusReader.Read read : answer.reads()) {
        if (!blocked(read.domain(), blocked)) {
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

  private boolean blocked(String domain, Map<String, Boolean> blocked) {
    return blocked.computeIfAbsent(domain, host -> serverBlocks.severityFor(host).isPresent());
  }

  // A server blocked here leaves at once.
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
