package com.example.short_link.post.application.write;

import com.example.short_link.common.event.NotesEmbeddedEvent;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostNoteQuoteRepository;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Notes a post quotes as cards: a kurl note URL on its own line (an EMBED block). Kept only for
// posts that are or were public, so draft autosaves stay at their usual statements and a first
// publish has nothing to clear. Only quotes a public post newly gains reach the notes' authors.
@Component
public class PostNoteQuotes {

  private static final Pattern NOTE_PATH = Pattern.compile(".*/notes/(\\d{1,18})/?");
  private static final int MAX = 50;

  private final PostNoteQuoteRepository quotes;
  private final ApplicationEventPublisher events;
  private final JsonMapper json;
  private final Set<String> hosts;

  public PostNoteQuotes(
      PostNoteQuoteRepository quotes,
      ApplicationEventPublisher events,
      JsonMapper json,
      @Value("${short-link.base-url}") String baseUrl,
      @Value("${short-link.frontend-base-url}") String frontendBaseUrl,
      @Value("${short-link.blog-base-url}") String blogBaseUrl) {
    this.quotes = quotes;
    this.events = events;
    this.json = json;
    this.hosts =
        Stream.of(baseUrl, frontendBaseUrl, blogBaseUrl)
            .map(PostNoteQuotes::host)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
  }

  public void index(PostEntity post, List<PostBlockEntity> blocks) {
    if (post.getStatus() != PostStatus.PUBLISHED && post.getStatus() != PostStatus.UNPUBLISHED) {
      return;
    }
    Set<Long> ids = noteIds(blocks);
    Set<Long> gained = new LinkedHashSet<>();
    if (post.getStatus() == PostStatus.PUBLISHED && !ids.isEmpty()) {
      gained.addAll(ids);
      gained.removeAll(quotes.noteIds(post.getId()));
    }
    quotes.replace(post.getId(), ids);
    announce(post, gained);
  }

  public void indexFirstPublish(PostEntity post, List<PostBlockEntity> blocks) {
    Set<Long> ids = noteIds(blocks);
    quotes.add(post.getId(), ids);
    announce(post, ids);
  }

  private void announce(PostEntity post, Set<Long> noteIds) {
    if (!noteIds.isEmpty()) {
      events.publishEvent(
          new NotesEmbeddedEvent(
              post.getUserId(),
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              Set.copyOf(noteIds)));
    }
  }

  Set<Long> noteIds(List<PostBlockEntity> blocks) {
    Set<Long> ids = new LinkedHashSet<>();
    for (PostBlockEntity block : blocks) {
      if (block.getType() != PostBlockType.EMBED || ids.size() >= MAX) {
        continue;
      }
      noteId(url(block.getContent())).ifPresent(ids::add);
    }
    return ids;
  }

  Optional<Long> noteId(String url) {
    if (url == null) {
      return Optional.empty();
    }
    try {
      URI uri = new URI(url.trim());
      String host = uri.getHost();
      if (host == null || !hosts.contains(host.toLowerCase(Locale.ROOT)) || uri.getPath() == null) {
        return Optional.empty();
      }
      Matcher m = NOTE_PATH.matcher(uri.getPath());
      return m.matches() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private String url(String content) {
    if (content == null || !content.startsWith("{")) {
      return content;
    }
    try {
      JsonNode node = json.readTree(content);
      return node.path("url").isString() ? node.path("url").stringValue() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static String host(String url) {
    try {
      String host = URI.create(url).getHost();
      return host == null ? null : host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
