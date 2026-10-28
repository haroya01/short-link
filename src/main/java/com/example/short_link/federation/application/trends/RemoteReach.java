package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.foryou.ReachSchedule;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counted;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counts;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Due;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Postponed;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Mastodon 4.3 answers /api/v1/statuses?id[]= with the statuses it still shows and leaves out the
// rest; an older server answers that path 404 and is asked /api/v1/statuses/{id} instead.
@Slf4j
@Service
public class RemoteReach {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final ReachProperties props;
  private final LatestProperties latestProps;
  private final RemoteNoteReachRepository reach;
  private final LatestStore store;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final FederationDomainBlockRepository serverBlocks;
  private final JsonMapper json;
  private final MeterRegistry meters;
  private final Clock clock;
  private final Set<String> oneByOne = ConcurrentHashMap.newKeySet();
  private final HostRest rests;

  private LocalDate day;
  private int askedToday;

  private record Answer(Map<String, Counts> found, boolean answered) {}

  public RemoteReach(
      ReachProperties props,
      LatestProperties latestProps,
      RemoteNoteReachRepository reach,
      LatestStore store,
      FederationHttp http,
      SigningKeys signingKeys,
      FederationDomainBlockRepository serverBlocks,
      JsonMapper json,
      MeterRegistry meters,
      Clock clock) {
    this.props = props;
    this.latestProps = latestProps;
    this.reach = reach;
    this.store = store;
    this.http = http;
    this.signingKeys = signingKeys;
    this.serverBlocks = serverBlocks;
    this.json = json;
    this.meters = meters;
    this.clock = clock;
    this.rests = new HostRest(clock);
  }

  void trended(List<TrendingStatusReader.Read> reads, Set<String> keepable) {
    if (reads.isEmpty()) {
      return;
    }
    Map<String, TrendingStatusReader.Read> byUri = new LinkedHashMap<>();
    reads.forEach(read -> byUri.putIfAbsent(read.note().uri(), read));
    Map<String, Long> kept = reach.idsByUri(byUri.keySet());
    if (latestProps.pulling()) {
      byUri.values().stream()
          .filter(read -> !kept.containsKey(read.note().uri()))
          .filter(read -> keepable.contains(read.note().uri()))
          .forEach(read -> store.keepTrended(read.note().sourceHost(), read));
    }
    Map<Long, Counts> counts = new HashMap<>();
    kept.forEach(
        (uri, noteId) -> {
          TrendingStatusReader.Read read = byUri.get(uri);
          if (read != null) {
            TrendingNote note = read.note();
            counts.put(noteId, new Counts(note.likeCount(), note.repostCount(), note.replyCount()));
          }
        });
    reach.trended(counts, clock.instant());
  }

  public int round() {
    if (!props.enabled() || !latestProps.pulling()) {
      return 0;
    }
    Instant now = clock.instant();
    Map<String, List<Due>> byHost =
        reach.due(now.minus(ReachSchedule.LATE), now, props.perRound()).stream()
            .collect(Collectors.groupingBy(Due::host, LinkedHashMap::new, Collectors.toList()));
    List<Counted> counted = new ArrayList<>();
    List<Postponed> postponed = new ArrayList<>();
    Signer signer = null;
    boolean first = true;
    for (Map.Entry<String, List<Due>> host : byHost.entrySet()) {
      if (rests.resting(host.getKey(), now)) {
        continue;
      }
      if (serverBlocks.severityFor(host.getKey()).isPresent()) {
        host.getValue().forEach(due -> postponed.add(new Postponed(due.noteId(), null, 0, null)));
        continue;
      }
      if (signer == null) {
        signer = signingKeys.forInstance();
      }
      List<List<Due>> chunks = chunks(host.getKey(), host.getValue());
      for (List<Due> chunk : chunks) {
        if (!spend()) {
          meters.counter("federation_reach", "result", "capped").increment(chunk.size());
          break;
        }
        if (!first) {
          pause();
        }
        first = false;
        Optional<Answer> answer = ask(host.getKey(), chunk, signer);
        if (answer.isEmpty()) {
          break;
        }
        settle(chunk, answer.get(), now, counted, postponed);
        if (!answer.get().answered()) {
          break;
        }
      }
    }
    reach.counted(counted);
    reach.postponed(postponed);
    return counted.size();
  }

  private List<List<Due>> chunks(String host, List<Due> due) {
    int size = oneByOne.contains(host) ? 1 : ReachProperties.PER_REQUEST;
    List<List<Due>> chunks = new ArrayList<>();
    for (int from = 0; from < due.size(); from += size) {
      chunks.add(due.subList(from, Math.min(from + size, due.size())));
    }
    return chunks;
  }

  private void settle(
      List<Due> chunk,
      Answer answer,
      Instant now,
      List<Counted> counted,
      List<Postponed> postponed) {
    for (Due due : chunk) {
      Counts counts = answer.found().get(due.statusId());
      if (counts != null) {
        int checks = due.checks() + 1;
        counted.add(
            new Counted(due.noteId(), counts, now, ReachSchedule.afterCheck(checks, now), checks));
        meters.counter("federation_reach", "result", "counted").increment();
      } else if (answer.answered()) {
        postponed.add(new Postponed(due.noteId(), null, due.failures(), now));
        meters.counter("federation_reach", "result", "gone").increment();
      } else {
        int failures = due.failures() + 1;
        postponed.add(
            new Postponed(due.noteId(), ReachSchedule.afterFailure(failures, now), failures, null));
        meters.counter("federation_reach", "result", "failed").increment();
      }
    }
  }

  // Empty when the server is to rest or turned out to want single asks: the chunk stays due.
  private Optional<Answer> ask(String host, List<Due> chunk, Signer signer) {
    boolean single = oneByOne.contains(host);
    String path =
        single
            ? "/api/v1/statuses/" + chunk.getFirst().statusId()
            : "/api/v1/statuses?"
                + chunk.stream()
                    .map(due -> "id%5B%5D=" + due.statusId())
                    .collect(Collectors.joining("&"));
    meters.counter("federation_reach_requests", "host", host).increment();
    FederationHttp.Result result =
        http.getJson(URI.create("https://" + host + path), signer, props.patience());
    if (result instanceof FederationHttp.Result.Failed failed) {
      int status = failed.status();
      if (status == 429 || failed.deniedAccess()) {
        rest(host);
        meters.counter("federation_reach", "result", "limited").increment(chunk.size());
        return Optional.empty();
      }
      if (!single && (status == 404 || status == 400 || status == 422)) {
        oneByOne.add(host);
        return Optional.empty();
      }
      if (single && (status == 404 || status == 410)) {
        return Optional.of(new Answer(Map.of(), true));
      }
    }
    if (!(result instanceof FederationHttp.Result.Ok ok)) {
      log.info("federation reach unanswered host={} outcome={}", host, result);
      return Optional.of(new Answer(Map.of(), false));
    }
    JsonNode body;
    try {
      body = json.readTree(ok.body());
    } catch (JacksonException malformed) {
      return Optional.of(new Answer(Map.of(), false));
    }
    Map<String, Counts> found = new HashMap<>();
    for (JsonNode status : body == null ? List.<JsonNode>of() : single ? List.of(body) : body) {
      String id = status.path("id").asString(null);
      if (id != null) {
        found.put(
            id,
            new Counts(
                count(status.get("favourites_count")),
                count(status.get("reblogs_count")),
                count(status.get("replies_count"))));
      }
    }
    boolean answered = body != null && (single ? body.isObject() : body.isArray());
    if (answered) {
      rests.answered(host);
    }
    return Optional.of(new Answer(found, answered));
  }

  private void rest(String host) {
    log.info("federation reach resting host={} wait={}", host, rests.rest(host));
  }

  private synchronized boolean spend() {
    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    if (!today.equals(day)) {
      day = today;
      askedToday = 0;
    }
    if (askedToday >= props.perDay()) {
      return false;
    }
    askedToday++;
    return true;
  }

  private void pause() {
    try {
      Thread.sleep(props.pause());
    } catch (InterruptedException stopping) {
      Thread.currentThread().interrupt();
    }
  }

  private static long count(JsonNode node) {
    return node != null && node.isIntegralNumber() && node.longValue() > 0 ? node.longValue() : 0;
  }
}
