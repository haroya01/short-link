package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.application.BlogFeed;
import com.example.short_link.blogimport.application.BlogFeeds;
import com.example.short_link.blogimport.application.HostGate;
import com.example.short_link.blogimport.application.feed.ConvertedBody;
import com.example.short_link.blogimport.application.feed.HtmlBlockConverter;
import com.example.short_link.blogimport.domain.FeedValidators;
import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.ImportSourceStatus;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.common.link.ShortLinkIssuer;
import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.post.application.image.PostImageService;
import com.example.short_link.post.application.write.BeginImportedPostCommand;
import com.example.short_link.post.application.write.ImportedPostWriter;
import com.example.short_link.post.application.write.PublishImportedPostCommand;
import com.example.short_link.post.application.write.ReplacePostBlocksCommand.BlockInput;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.SourceUrlHash;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// A first import (status IMPORTING) never announces: old posts must not flood followers' and
// federated feeds. A sync announces only originals published after the source was verified.
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogImportRunner {

  static final int MAX_IMAGES_PER_POST = 30;
  static final int ANNOUNCE_EXCERPT_LENGTH = 120;
  private static final Duration LOCK_TTL = Duration.ofMinutes(20);

  private final ImportSourceRepository sources;
  private final BlogFeeds feeds;
  private final HostGate hosts;
  private final HtmlBlockConverter html;
  private final ImportLedger ledger;
  private final ImportSourceRuns runs;
  private final ImportedPostWriter posts;
  private final PostImageService images;
  private final ShortLinkIssuer shortLinks;
  private final NoteCommandService notes;
  private final RedisDistributedLock lock;
  private final UserRepository users;
  private final Clock clock;

  enum Outcome {
    IMPORTED,
    SKIPPED,
    FAILED
  }

  public void run(Long sourceId) {
    String key = "kurl:blog-import:source:" + sourceId;
    if (!lock.tryAcquire(key, LOCK_TTL)) {
      return;
    }
    try {
      runLocked(sourceId);
    } finally {
      lock.release(key);
    }
  }

  private void runLocked(Long sourceId) {
    ImportSourceEntity source = sources.findById(sourceId).orElse(null);
    if (source == null || !source.isVerified() || !writerActive(source.getUserId())) {
      return;
    }
    boolean firstImport = source.getStatus() == ImportSourceStatus.IMPORTING;
    BlogFeed feed;
    try {
      Optional<BlogFeed> changed =
          firstImport
              ? Optional.of(feeds.readRecent(source.address()))
              : feeds.readIfChanged(source.address(), source.validators());
      if (changed.isEmpty()) {
        runs.notModified(sourceId);
        return;
      }
      feed = changed.get();
    } catch (BlogImportException e) {
      runs.fail(sourceId, e.code());
      return;
    }
    Map<String, BlogFeed.Post> byHash = new LinkedHashMap<>();
    for (BlogFeed.Post post : feed.posts()) {
      byHash.putIfAbsent(SourceUrlHash.of(post.url()), post);
    }
    Map<String, ImportItemEntity> taken = ledger.taken(sourceId, byHash.keySet());
    List<BlogFeed.Post> pending = new ArrayList<>();
    for (Map.Entry<String, BlogFeed.Post> entry : byHash.entrySet()) {
      ImportItemEntity item = taken.get(entry.getKey());
      if (item == null || !item.isSettled()) {
        pending.add(entry.getValue());
      }
    }
    pending.sort(
        Comparator.comparing(
            BlogFeed.Post::publishedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    if (!pending.isEmpty()
        && !runs.start(sourceId, pending.size(), feed.title(), feed.fullContent())) {
      return;
    }
    int done = 0;
    int failed = 0;
    for (BlogFeed.Post post : pending) {
      Outcome outcome =
          importOne(source, feed, post, taken.get(SourceUrlHash.of(post.url())), !firstImport);
      if (outcome == Outcome.IMPORTED) {
        done++;
      } else if (outcome == Outcome.FAILED) {
        failed++;
      }
      if (!runs.progress(sourceId, done, failed)) {
        return;
      }
    }
    runs.finish(
        sourceId,
        feed.title(),
        feed.fullContent(),
        failed == 0 ? feed.validators() : FeedValidators.NONE);
  }

  private boolean writerActive(Long userId) {
    return users
        .findById(userId)
        .filter(user -> !user.isDeleted() && user.canWriteAt(clock.instant()))
        .isPresent();
  }

  Outcome importOne(
      ImportSourceEntity source,
      BlogFeed feed,
      BlogFeed.Post post,
      ImportItemEntity existing,
      boolean sync) {
    Long itemId;
    Long resumePostId = null;
    if (existing == null) {
      try {
        itemId = ledger.claim(source.getId(), SourceUrlHash.of(post.url()));
      } catch (DataIntegrityViolationException taken) {
        return Outcome.SKIPPED;
      }
    } else {
      itemId = existing.getId();
      resumePostId = existing.getPostId();
    }
    try {
      Outcome outcome = publish(source, feed, post, itemId, resumePostId, sync);
      if (outcome == Outcome.FAILED) {
        ledger.fail(itemId);
      }
      return outcome;
    } catch (RuntimeException e) {
      log.warn("blog import failed source={} url={}: {}", source.getId(), post.url(), e.toString());
      ledger.fail(itemId);
      return Outcome.FAILED;
    }
  }

  private Outcome publish(
      ImportSourceEntity source,
      BlogFeed feed,
      BlogFeed.Post post,
      Long itemId,
      Long resumePostId,
      boolean sync) {
    Long userId = source.getUserId();
    Prepared prepared = prepare(feed, post);
    Optional<Long> started =
        posts.begin(
            new BeginImportedPostCommand(
                userId, source.getId(), source.getPlatform().name(), post.url(), resumePostId));
    if (started.isEmpty()) {
      ledger.complete(itemId);
      return Outcome.SKIPPED;
    }
    Long postId = started.get();
    ledger.attach(itemId, postId);

    Map<String, Rehosted> rehosted = new HashMap<>();
    for (String url : prepared.imageUrls()) {
      if (rehosted.size() >= MAX_IMAGES_PER_POST) {
        break;
      }
      rehosted.put(url, rehost(userId, postId, url));
    }
    List<BlockInput> blocks =
        prepared.body() == null
            ? prepared.excerptBlocks()
            : html.blocks(
                prepared.body(), url -> rehosted.getOrDefault(url, Rehosted.of(url)).url());
    Rehosted cover =
        prepared.coverUrl() == null
            ? null
            : rehosted.getOrDefault(prepared.coverUrl(), Rehosted.of(prepared.coverUrl()));

    Optional<ShortLinkIssuer.Issued> link = shortLinks.issue(userId, post.url());
    boolean published =
        posts.publish(
            new PublishImportedPostCommand(
                userId,
                postId,
                prepared.title(),
                prepared.excerpt(),
                feed.languageTag(),
                post.tags(),
                blocks,
                cover == null ? null : cover.url(),
                cover == null ? null : cover.key(),
                prepared.body() == null && cover != null,
                prepared.body() == null,
                link.map(ShortLinkIssuer.Issued::linkId).orElse(null),
                link.map(ShortLinkIssuer.Issued::shortCode).orElse(null),
                !source.isShowInDiscover(),
                post.publishedAt()));
    if (!published) {
      return Outcome.FAILED;
    }
    ledger.complete(itemId);
    if (sync && source.announces(post.publishedAt())) {
      announce(userId, prepared, link.map(ShortLinkIssuer.Issued::shortUrl).orElse(post.url()));
    }
    return Outcome.IMPORTED;
  }

  private Prepared prepare(BlogFeed feed, BlogFeed.Post post) {
    if (feed.fullContent()) {
      ConvertedBody body = html.convert(post.html(), post.url());
      if (!body.isEmpty()) {
        List<String> imageUrls = List.copyOf(body.imageUrls());
        return new Prepared(
            title(post.title(), body.plainText()),
            body.plainText(),
            body,
            List.of(),
            imageUrls,
            imageUrls.isEmpty() ? null : imageUrls.get(0));
      }
    }
    HtmlBlockConverter.Summary summary = html.summarize(post.html(), post.url());
    String excerpt = HtmlBlockConverter.excerptOf(summary.text());
    List<BlockInput> blocks =
        summary.text().isBlank()
            ? List.of()
            : List.of(
                new BlockInput(
                    PostBlockType.PARAGRAPH, HtmlBlockConverter.paragraphOf(summary.text())));
    return new Prepared(
        title(post.title(), summary.text()),
        excerpt,
        null,
        blocks,
        summary.imageUrl() == null ? List.of() : List.of(summary.imageUrl()),
        summary.imageUrl());
  }

  private Rehosted rehost(Long userId, Long postId, String url) {
    try {
      PostImageService.CommitResult result =
          hosts.call(url, () -> images.importFromUrl(userId, postId, url));
      return new Rehosted(result.imageUrl(), result.key());
    } catch (RuntimeException e) {
      return Rehosted.of(url);
    }
  }

  private void announce(Long userId, Prepared prepared, String url) {
    String body =
        unmention(prepared.title())
            + "\n"
            + unmention(clip(prepared.excerpt(), ANNOUNCE_EXCERPT_LENGTH))
            + "\n"
            + url;
    try {
      notes.create(userId, new NoteDraft(body, List.of(), null, null, null, null, false, "public"));
    } catch (RuntimeException e) {
      log.warn("blog import announcement skipped for user {}: {}", userId, e.toString());
    }
  }

  static String title(String raw, String text) {
    String title = raw == null ? "" : raw.strip();
    if (title.isEmpty()) {
      title = clip(text == null ? "" : text.strip(), 60);
    }
    if (title.isEmpty()) {
      title = "제목 없음";
    }
    return title.length() > 200 ? title.substring(0, 200) : title;
  }

  // A blog's own "@word" (an annotation, an account elsewhere) must not mention a kurl member.
  static String unmention(String text) {
    return text.replace('@', '＠');
  }

  static String clip(String text, int codePoints) {
    if (text.codePointCount(0, text.length()) <= codePoints) {
      return text;
    }
    return text.substring(0, text.offsetByCodePoints(0, codePoints - 1)).strip() + "…";
  }

  record Prepared(
      String title,
      String excerpt,
      ConvertedBody body,
      List<BlockInput> excerptBlocks,
      List<String> imageUrls,
      String coverUrl) {}

  record Rehosted(String url, String key) {
    static Rehosted of(String url) {
      return new Rehosted(url, null);
    }
  }
}
