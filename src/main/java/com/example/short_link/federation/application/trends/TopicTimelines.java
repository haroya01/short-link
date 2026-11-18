package com.example.short_link.federation.application.trends;

import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicLanguage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
class TopicTimelines {

  static final int PER_SERVER = 40;
  static final int SERVERS = 3;
  static final int SAMPLE = 20;

  private final TrendsProperties props;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final FederationDomainBlockRepository serverBlocks;
  private final RemoteAccountStandingReader standings;
  private final TrendingStatusReader reader;
  private final JsonMapper json;
  private final ExecutorService askers = Executors.newVirtualThreadPerTaskExecutor();

  record Sample(String language, List<TrendingStatusReader.Read> reads) {}

  Optional<Sample> sample(Topic topic, String host, Signer signer) {
    return array(timeline(host, topic, SAMPLE), signer)
        .map(
            statuses ->
                new Sample(
                    TopicLanguage.sampled(languages(statuses)),
                    allowed(List.of(reads(statuses, host)))));
  }

  List<TrendingStatusReader.Read> posts(Topic topic, List<String> hosts) {
    if (hosts.isEmpty()) {
      return List.of();
    }
    Signer signer = signingKeys.forInstance();
    List<CompletableFuture<List<TrendingStatusReader.Read>>> asks =
        hosts.stream()
            .limit(SERVERS)
            .map(
                host ->
                    CompletableFuture.supplyAsync(() -> page(host, topic, signer), askers)
                        .exceptionally(
                            failed -> {
                              log.warn(
                                  "federation topic timeline failed host={} reason={}",
                                  host,
                                  failed.toString());
                              return List.of();
                            }))
            .toList();
    return allowed(asks.stream().map(CompletableFuture::join).toList());
  }

  private List<TrendingStatusReader.Read> page(String host, Topic topic, Signer signer) {
    return array(timeline(host, topic, PER_SERVER), signer)
        .map(statuses -> reads(statuses, host))
        .orElse(List.of());
  }

  Optional<JsonNode> array(URI uri, Signer signer) {
    FederationHttp.Result result = http.getJson(uri, signer, props.patience());
    if (!(result instanceof FederationHttp.Result.Ok ok)) {
      log.info("federation topics unanswered host={} outcome={}", uri.getHost(), result);
      return Optional.empty();
    }
    try {
      JsonNode rows = json.readTree(ok.body());
      return rows != null && rows.isArray() ? Optional.of(rows) : Optional.empty();
    } catch (JacksonException malformed) {
      log.info("federation topics malformed host={}", uri.getHost());
      return Optional.empty();
    }
  }

  static URI timeline(String host, Topic topic, int limit) {
    String path =
        topic.isTag()
            ? "/api/v1/timelines/tag/"
                + UriUtils.encodePathSegment(topic.tag(), StandardCharsets.UTF_8)
                + "?limit="
            : "/api/v1/timelines/link?url="
                + UriUtils.encode(topic.article().url(), StandardCharsets.UTF_8)
                + "&limit=";
    return URI.create("https://" + host + path + limit);
  }

  private List<TrendingStatusReader.Read> reads(JsonNode statuses, String host) {
    List<TrendingStatusReader.Read> reads = new ArrayList<>();
    for (JsonNode status : statuses) {
      if (status.isObject()) {
        reader.read(status, host).ifPresent(reads::add);
      }
    }
    return reads;
  }

  private static List<String> languages(JsonNode statuses) {
    List<String> languages = new ArrayList<>();
    for (JsonNode status : statuses) {
      String language = text(status.get("language"));
      if (language != null) {
        languages.add(language);
      }
    }
    return languages;
  }

  private List<TrendingStatusReader.Read> allowed(List<List<TrendingStatusReader.Read>> answers) {
    BlockedHosts blocked = new BlockedHosts(serverBlocks);
    List<List<TrendingStatusReader.Read>> unblocked =
        answers.stream()
            .map(
                answer -> answer.stream().filter(read -> !blocked.contains(read.domain())).toList())
            .toList();
    return TrendingPosts.known(TrendingPosts.blend(unblocked), standings);
  }
}
