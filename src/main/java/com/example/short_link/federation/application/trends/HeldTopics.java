package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.domain.topics.Topic;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class HeldTopics {

  static final Duration FRESH = Duration.ofMinutes(10);
  static final HeldTopics NONE = new HeldTopics(List.of());

  private final List<Topic> topics;
  private final Map<String, Topic> byId = new HashMap<>();
  private final Map<String, CompletableFuture<Posts>> posts = new ConcurrentHashMap<>();

  private record Posts(List<TrendingStatusReader.Read> reads, Instant readAt) {}

  HeldTopics(List<Topic> topics) {
    this.topics = List.copyOf(topics);
    this.topics.forEach(topic -> byId.put(topic.id(), topic));
  }

  List<Topic> topics() {
    return topics;
  }

  Optional<Topic> find(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  int postsHeld() {
    return posts.size();
  }

  void seed(String id, List<TrendingStatusReader.Read> reads, Instant readAt) {
    if (byId.containsKey(id)) {
      posts.put(id, CompletableFuture.completedFuture(new Posts(reads, readAt)));
    }
  }

  List<TrendingStatusReader.Read> posts(
      Topic topic, Instant now, Supplier<List<TrendingStatusReader.Read>> read) {
    if (!byId.containsKey(topic.id())) {
      return List.of();
    }
    CompletableFuture<Posts> mine = new CompletableFuture<>();
    CompletableFuture<Posts> flight =
        posts.compute(topic.id(), (id, held) -> held == null || stale(held, now) ? mine : held);
    if (flight == mine) {
      try {
        mine.complete(new Posts(read.get(), now));
      } catch (RuntimeException failed) {
        posts.remove(topic.id(), mine);
        mine.completeExceptionally(failed);
        throw failed;
      }
    }
    return flight.join().reads();
  }

  private static boolean stale(CompletableFuture<Posts> held, Instant now) {
    return held.isDone()
        && (held.isCompletedExceptionally() || held.join().readAt().plus(FRESH).isBefore(now));
  }

  HeldTopics without(String domain) {
    List<Topic> left = new ArrayList<>();
    for (Topic topic : topics) {
      List<String> servers = topic.servers().stream().filter(host -> !under(host, domain)).toList();
      if (!servers.isEmpty()) {
        left.add(servers.size() == topic.servers().size() ? topic : topic.on(servers));
      }
    }
    HeldTopics next = new HeldTopics(left);
    posts.forEach(
        (id, held) -> {
          if (held.isDone() && !held.isCompletedExceptionally()) {
            Posts read = held.join();
            next.seed(
                id,
                read.reads().stream().filter(post -> !under(post.domain(), domain)).toList(),
                read.readAt());
          }
        });
    return next;
  }

  private static boolean under(String host, String domain) {
    return host.equals(domain) || host.endsWith("." + domain);
  }
}
