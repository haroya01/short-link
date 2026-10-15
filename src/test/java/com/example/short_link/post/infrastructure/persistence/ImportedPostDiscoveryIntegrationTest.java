package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.blogimport.domain.BlogAddress;
import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportItemRepository;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.repository.ImportedPostRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// A source hidden from discover keeps its posts off every surface the quality floor guards, and
// only those: the author's page still lists them.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportedPostDiscoveryIntegrationTest {

  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private ImportedPostRepository importedPosts;
  @Autowired private ImportSourceRepository sources;
  @Autowired private ImportItemRepository items;
  @Autowired private LinkRepository links;
  @Autowired private PostHighlightRepository highlights;
  @Autowired private SeriesRepository series;
  @Autowired private SeriesItemRepository seriesItems;
  @Autowired private SeriesItemReader seriesReader;
  @Autowired private EntityManager em;

  private final String suffix = UUID.randomUUID().toString().substring(0, 8);
  private final String tag = "imported-" + suffix;
  private Long authorId;
  private Long viewerId;
  private ImportSourceEntity source;
  private PostEntity shown;
  private PostEntity hidden;

  private Long user(String name) {
    UserEntity user = new UserEntity(name + "@x.com", "google", "g-" + name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private PostEntity imported(String path, boolean hiddenFromDiscover, Long linkId, String code) {
    PostEntity post =
        PostEntity.importedDraft(
            authorId, "draft-" + path, source.getId(), "VELOG", "https://velog.io/@w/" + path);
    post.updateTitle("가져온 " + path);
    post.updateTags(List.of(tag));
    DiscoverableBodies.discoverable(post);
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    post.publishImported(false, linkId, code, hiddenFromDiscover, now, now);
    return posts.save(post);
  }

  @BeforeEach
  void importTwoPostsOneHidden() {
    authorId = user("ia" + suffix);
    viewerId = user("iv" + suffix);
    source =
        sources.save(
            new ImportSourceEntity(
                authorId,
                BlogAddress.parse("velog.io/@w" + suffix).orElseThrow(),
                "w",
                true,
                "kurl-AAAAAA"));
    shown = imported("shown-" + suffix, false, null, null);
    hidden = imported("hidden-" + suffix, true, null, null);
    posts.flush();
  }

  private static List<Long> ids(List<PostEntity> found) {
    return found.stream().map(PostEntity::getId).toList();
  }

  @Test
  void browseTagAndRecommendationFeedsLeaveOutHiddenSources() {
    assertThat(ids(posts.findPublishedRecent(null, null, 0, 500)))
        .contains(shown.getId())
        .doesNotContain(hidden.getId());
    assertThat(ids(posts.findPublishedByTag(null, tag, 0, 50))).containsExactly(shown.getId());
    assertThat(ids(posts.findPublishedTrendingByTag(null, tag, 0, 50)))
        .containsExactly(shown.getId());
    assertThat(posts.countPublishedByTag(null, tag)).isEqualTo(1);
    assertThat(ids(posts.findForYouCandidates(viewerId, List.of(tag), List.of(), 0, 50)))
        .containsExactly(shown.getId());
    assertThat(posts.countForYouCandidates(viewerId, List.of(tag), List.of())).isEqualTo(1);
    assertThat(posts.findPopularTags(10_000))
        .filteredOn(t -> t.tag().equals(tag))
        .extracting(TagCount::count)
        .containsExactly(1L);
    assertThat(posts.findTopAuthorStats(null, 10_000))
        .filteredOn(s -> s.authorId().equals(authorId))
        .extracting(AuthorPostStats::postCount)
        .containsExactly(1L);
    assertThat(shown.isDiscoverable()).isTrue();
    assertThat(hidden.isDiscoverable()).isFalse();
  }

  @Test
  void highlightAndSeriesDiscoveryLeaveOutHiddenSources() {
    highlights.save(
        new PostHighlightEntity(shown.getId(), viewerId, 0, 0, 0, 3, "shown-" + suffix, null));
    highlights.save(
        new PostHighlightEntity(hidden.getId(), viewerId, 0, 0, 0, 3, "hidden-" + suffix, null));
    SeriesEntity hiddenSeries = series.save(new SeriesEntity(authorId, "s-" + suffix, "s"));
    seriesItems.replace(
        hiddenSeries.getId(),
        List.of(
            new SeriesItemEntity(hiddenSeries.getId(), SeriesItemType.POST, hidden.getId(), 0)));
    em.flush();

    assertThat(highlights.findRecentOnPublishedPosts(null, 0, 500))
        .extracting(PostHighlightEntity::getQuote)
        .contains("shown-" + suffix)
        .doesNotContain("hidden-" + suffix);
    assertThat(seriesReader.activeSeries(null, 1, 10_000))
        .extracting(SeriesActivity::seriesId)
        .doesNotContain(hiddenSeries.getId());
  }

  @Test
  void theAuthorPageStillListsHiddenPosts() {
    assertThat(
            ids(
                posts.findAllByUserIdAndStatusOrderByPublishedAtDesc(
                    authorId, com.example.short_link.post.domain.PostStatus.PUBLISHED)))
        .contains(shown.getId(), hidden.getId());
  }

  @Test
  void aSourcesPostsAreCountedHiddenAndListedTogether() {
    assertThat(importedPosts.countPublishedByImportSourceIds(List.of(source.getId())))
        .containsEntry(source.getId(), 2L);
    assertThat(importedPosts.countPublishedByImportSourceIds(List.of())).isEmpty();
    assertThat(importedPosts.findIdsByImportSourceId(source.getId()))
        .containsExactly(shown.getId(), hidden.getId());

    assertThat(importedPosts.updateHiddenFromDiscover(source.getId(), false)).isEqualTo(2);
    em.clear();
    assertThat(ids(posts.findPublishedByTag(null, tag, 0, 50)))
        .containsExactlyInAnyOrder(shown.getId(), hidden.getId());
  }

  @Test
  void deletingTheShortLinkOrTheSourceKeepsThePost() {
    LinkEntity link =
        links.save(new LinkEntity("https://velog.io/@w/l", "ip" + suffix, authorId, null));
    PostEntity linked = imported("linked-" + suffix, false, link.getId(), "ip" + suffix);
    posts.flush();
    em.clear();

    em.createNativeQuery("DELETE FROM link WHERE id = :id")
        .setParameter("id", link.getId())
        .executeUpdate();
    em.createNativeQuery("DELETE FROM blog_import_source WHERE id = :id")
        .setParameter("id", source.getId())
        .executeUpdate();
    em.clear();

    PostEntity reread = posts.findById(linked.getId()).orElseThrow();
    assertThat(reread.getSourceLinkId()).isNull();
    assertThat(reread.getImportSourceId()).isNull();
    assertThat(reread.getSourceUrl()).isEqualTo("https://velog.io/@w/linked-" + suffix);
    assertThat(reread.isImported()).isTrue();
  }

  @Test
  void anOriginalComesInOncePerAuthor() {
    PostEntity twice =
        PostEntity.importedDraft(
            authorId, "draft-again", source.getId(), "VELOG", shown.getSourceUrl());
    twice.updateTitle("again");

    assertThatThrownBy(
            () -> {
              posts.save(twice);
              posts.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aBlogHasOneVerifiedOwnerAndALedgerRowPerOriginal() {
    items.saveAndFlush(new ImportItemEntity(source.getId(), "hash-" + suffix));
    assertThatThrownBy(
            () -> items.saveAndFlush(new ImportItemEntity(source.getId(), "hash-" + suffix)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void twoMembersCannotBothVerifyTheSameBlog() {
    source.verify(Instant.now(), 0);
    sources.saveAndFlush(source);
    ImportSourceEntity rival =
        new ImportSourceEntity(
            viewerId,
            BlogAddress.parse("velog.io/@w" + suffix).orElseThrow(),
            "w",
            true,
            "kurl-BBBBBB");
    rival.verify(Instant.now(), 0);

    assertThat(sources.isClaimedByAnother(source.getBlogKey(), viewerId)).isTrue();
    assertThat(sources.isClaimedByAnother(source.getBlogKey(), authorId)).isFalse();
    assertThatThrownBy(() -> sources.saveAndFlush(rival))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dueSourcesAreVerifiedOnesNotSyncedLatelyAndStalledFirstImports() {
    Instant now = Instant.now();
    source.verify(now.minus(2, ChronoUnit.HOURS), 0);
    source.finishRun(now.minus(2, ChronoUnit.HOURS));
    sources.saveAndFlush(source);
    ImportSourceEntity fresh =
        new ImportSourceEntity(
            authorId,
            BlogAddress.parse("blog.naver.com/f" + suffix).orElseThrow(),
            "f",
            false,
            "kurl-CCCCCC");
    fresh.verify(now, 0);
    fresh.finishRun(now);
    sources.saveAndFlush(fresh);
    ImportSourceEntity pending =
        sources.saveAndFlush(
            new ImportSourceEntity(
                authorId,
                BlogAddress.parse("p" + suffix + ".tistory.com").orElseThrow(),
                "p",
                true,
                "kurl-DDDDDD"));

    List<Long> due =
        sources.findDueIds(
            now.minus(25, ChronoUnit.MINUTES), now.minus(30, ChronoUnit.MINUTES), 10_000);

    assertThat(due).contains(source.getId()).doesNotContain(fresh.getId(), pending.getId());
    assertThat(sources.findAllByUserId(authorId))
        .extracting(ImportSourceEntity::getId)
        .containsExactly(pending.getId(), fresh.getId(), source.getId());
    assertThat(sources.countActiveByUserId(authorId)).isEqualTo(3);
    assertThat(sources.findByUserIdAndBlogKey(authorId, fresh.getBlogKey())).contains(fresh);
  }
}
