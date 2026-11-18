package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicFolding;
import com.example.short_link.federation.domain.topics.TopicLanguage;
import com.example.short_link.federation.domain.topics.TopicMerge;
import com.example.short_link.federation.domain.topics.TopicRanking;
import com.example.short_link.federation.domain.topics.TopicSighting;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class TrendingTopics {

  static final int PER_HOST = 20;
  static final int OWN_DAYS = 2;
  static final int OWN_MIN_ACCOUNTS = 2;

  private final TrendsProperties props;
  private final SigningKeys signingKeys;
  private final FederationDomainBlockRepository serverBlocks;
  private final NoteTopicReader notes;
  private final TopicTimelines timelines;
  private final FederationUrls urls;
  private final Clock clock;
  private final HostBackoff backoff = new HostBackoff();

  private volatile HeldTopics held = HeldTopics.NONE;

  HeldTopics held() {
    return held;
  }

  List<String> elsewhere(Topic topic) {
    return topic.servers().stream().filter(host -> !host.equals(urls.domain())).toList();
  }

  public void withdraw(String domain) {
    held = held.without(domain);
  }

  public void refresh() {
    BlockedHosts blocked = new BlockedHosts(serverBlocks);
    Signer signer = null;
    List<TopicSighting> sightings = new ArrayList<>();
    for (String server : props.hosts()) {
      if (backoff.waiting(server) || blocked.contains(server)) {
        continue;
      }
      if (signer == null) {
        signer = signingKeys.forInstance();
      }
      Optional<List<TopicSighting>> answer = ask(server, signer);
      if (answer.isEmpty()) {
        backoff.failed(server);
        continue;
      }
      backoff.answered(server);
      sightings.addAll(answer.get());
    }
    sightings.addAll(own());
    held = hold(TopicMerge.merge(sightings), signer);
  }

  private Optional<List<TopicSighting>> ask(String server, Signer signer) {
    Optional<JsonNode> tags = timelines.array(trends(server, "tags"), signer);
    Optional<JsonNode> links = timelines.array(trends(server, "links"), signer);
    if (tags.isEmpty() && links.isEmpty()) {
      return Optional.empty();
    }
    List<TopicSighting> sightings = new ArrayList<>();
    tags.ifPresent(rows -> sightings.addAll(TopicAnswers.tags(rows, server)));
    links.ifPresent(rows -> sightings.addAll(TopicAnswers.links(rows, server)));
    return Optional.of(sightings);
  }

  private static URI trends(String server, String kind) {
    return URI.create("https://" + server + "/api/v1/trends/" + kind + "?limit=" + PER_HOST);
  }

  private List<TopicSighting> own() {
    Instant now = clock.instant();
    List<TopicSighting> own =
        new ArrayList<>(
            TopicAnswers.ownTags(
                notes.trendingTags(now, OWN_DAYS, OWN_MIN_ACCOUNTS, PER_HOST), urls.domain()));
    own.addAll(
        TopicAnswers.ownLinks(
            notes.trendingLinks(now, OWN_DAYS, OWN_MIN_ACCOUNTS, PER_HOST), urls.domain()));
    return own;
  }

  private HeldTopics hold(List<Topic> merged, Signer signer) {
    Map<String, TopicTimelines.Sample> samples = samples(merged, signer);
    List<Topic> known =
        merged.stream()
            .map(
                topic ->
                    samples.containsKey(topic.id())
                        ? topic.in(samples.get(topic.id()).language())
                        : topic)
            .toList();
    HeldTopics next = new HeldTopics(TopicRanking.pool(TopicFolding.fold(known)));
    Instant now = clock.instant();
    samples.forEach(
        (id, sample) -> {
          if (!sample.reads().isEmpty()) {
            next.seed(id, sample.reads(), now);
          }
        });
    return next;
  }

  private Map<String, TopicTimelines.Sample> samples(List<Topic> merged, Signer signer) {
    Map<String, TopicTimelines.Sample> samples = new LinkedHashMap<>();
    merged.stream()
        .filter(topic -> topic.isTag() && topic.language() == null)
        .filter(topic -> !elsewhere(topic).isEmpty())
        .sorted(TopicRanking.ORDER)
        .limit(TopicLanguage.SAMPLED_TAGS)
        .forEach(
            topic ->
                timelines
                    .sample(topic, elsewhere(topic).getFirst(), signer)
                    .ifPresent(sample -> samples.put(topic.id(), sample)));
    return samples;
  }
}
