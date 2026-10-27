package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.post.application.access.PostAccessFixtures;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PublicPostQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private SeriesRepository seriesRepository;
  @Mock private SeriesItemReader seriesItemReader;
  @Mock private CtaRepository ctaRepository;
  @Mock private UserBlockChecker userBlocks;

  private static final Instant NOW = Instant.now();

  private PublicPostQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PublicPostQueryService(
            userRepository,
            postRepository,
            postBlockRepository,
            seriesRepository,
            seriesItemReader,
            ctaRepository,
            new com.example.short_link.link.application.ShortLinkUrlBuilder("https://kurl.me"),
            userBlocks,
            PostAccessFixtures.policy(),
            PostAccessFixtures.unlistedUrls(),
            PostAccessFixtures.rooms(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient().when(userBlocks.between(any(), any())).thenReturn(BlockRelation.NONE);
  }

  private UserEntity authorWithUsername(String username) {
    UserEntity user = new UserEntity("u@x.com", "google", "g-1");
    user.claimUsername(username);
    return user;
  }

  @Test
  void listReturnsPublishedPosts() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity p1 = new PostEntity(author.getId(), "post-1", "Post 1", "ko");
    p1.publish();
    PostEntity p2 = new PostEntity(author.getId(), "post-2", "Post 2", "ja");
    p2.publish();
    when(postRepository.findProfilePosts(author.getId(), null)).thenReturn(List.of(p1, p2));

    PublicPostListView response = service.listPublicPosts("john", PostReader.of(null)).body();

    assertThat(response.author().username()).isEqualTo("john");
    assertThat(response.posts()).hasSize(2);
    assertThat(response.posts().get(0).slug()).isEqualTo("post-1");
  }

  @Test
  void listNormalizesUsername() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findProfilePosts(author.getId(), null)).thenReturn(List.of());

    service.listPublicPosts("  JOHN  ", PostReader.of(null)).body();

    org.mockito.Mockito.verify(userRepository).findByUsername("john");
  }

  @Test
  void listUnknownUsernameThrowsProfileNotFound() {
    when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listPublicPosts("unknown", PostReader.of(null)).body())
        .isInstanceOf(ProfileException.class)
        .extracting(e -> ((ProfileException) e).errorCode())
        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  void listSoftDeletedUserThrowsProfileNotFound() {
    UserEntity author = authorWithUsername("john");
    author.softDelete();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));

    assertThatThrownBy(() -> service.listPublicPosts("john", PostReader.of(null)).body())
        .isInstanceOf(ProfileException.class)
        .extracting(e -> ((ProfileException) e).errorCode())
        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  void aBlockEitherWayListsNoPostsAndSaysWhich() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(userBlocks.between(9L, author.getId())).thenReturn(new BlockRelation(false, true));

    PublicPostListView response = service.listPublicPosts("john", PostReader.of(9L)).body();

    assertThat(response.posts()).isEmpty();
    assertThat(response.author().username()).isEqualTo("john");
    assertThat(response.blockedByViewer()).isFalse();
    assertThat(response.blocksViewer()).isTrue();
    org.mockito.Mockito.verifyNoInteractions(postRepository);
  }

  @Test
  void findReturnsPublishedPostWithBlocks() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "first-post", "First", "ko");
    post.publish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "first-post", null))
        .thenReturn(Optional.of(post));
    PostBlockEntity b1 = new PostBlockEntity(post.getId(), PostBlockType.PARAGRAPH, "Hello", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(b1));

    PublicPostDetail detail =
        service.findPublicPost("john", "first-post", PostReader.of(null), null).body();

    assertThat(detail.author().username()).isEqualTo("john");
    assertThat(detail.post().slug()).isEqualTo("first-post");
    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).type()).isEqualTo("PARAGRAPH");
  }

  private PostEntity publishedSeriesPost(UserEntity author, long id) {
    PostEntity post = new PostEntity(author.getId(), "part-" + id, "Part " + id, "ko");
    ReflectionTestUtils.setField(post, "id", id);
    post.publish();
    post.assignToSeries(5L, 2);
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "part-" + id, null))
        .thenReturn(Optional.of(post));
    SeriesEntity series = new SeriesEntity(author.getId(), "guide", "Guide");
    ReflectionTestUtils.setField(series, "id", 5L);
    when(seriesRepository.findById(5L)).thenReturn(Optional.of(series));
    return post;
  }

  @Test
  void seriesNavWalksThePostsAloneAndTheItemsTogether() {
    UserEntity author = authorWithUsername("john");
    publishedSeriesPost(author, 2L);
    when(seriesItemReader.readableEntries(5L))
        .thenReturn(
            List.of(
                new SeriesEntry(SeriesItemType.POST, 1L, "part-1", "Part 1", null, null),
                new SeriesEntry(SeriesItemType.NOTE, 40L, null, "a note", null, null),
                new SeriesEntry(SeriesItemType.POST, 2L, "part-2", "Part 2", null, null),
                new SeriesEntry(SeriesItemType.NOTE, 41L, null, "another note", null, null)));

    PublicPostSeriesNav nav =
        service.findPublicPost("john", "part-2", PostReader.of(null), null).body().series();

    assertThat(nav.slug()).isEqualTo("guide");
    assertThat(nav.position()).isEqualTo(2);
    assertThat(nav.total()).isEqualTo(2);
    assertThat(nav.prev()).isEqualTo(new PublicPostSeriesNav.NavLink("part-1", "Part 1"));
    assertThat(nav.next()).isNull();
    assertThat(nav.itemPosition()).isEqualTo(3);
    assertThat(nav.itemTotal()).isEqualTo(4);
    assertThat(nav.prevItem())
        .isEqualTo(new PublicPostSeriesNav.ItemLink("NOTE", null, 40L, "a note"));
    assertThat(nav.nextItem())
        .isEqualTo(new PublicPostSeriesNav.ItemLink("NOTE", null, 41L, "another note"));
  }

  @Test
  void seriesNavLinksAPostItemBySlug() {
    UserEntity author = authorWithUsername("john");
    publishedSeriesPost(author, 2L);
    when(seriesItemReader.readableEntries(5L))
        .thenReturn(
            List.of(
                new SeriesEntry(SeriesItemType.NOTE, 40L, null, "a note", null, null),
                new SeriesEntry(SeriesItemType.POST, 2L, "part-2", "Part 2", null, null),
                new SeriesEntry(SeriesItemType.POST, 3L, "part-3", "Part 3", null, null)));

    PublicPostSeriesNav nav =
        service.findPublicPost("john", "part-2", PostReader.of(null), null).body().series();

    assertThat(nav.position()).isEqualTo(1);
    assertThat(nav.prev()).isNull();
    assertThat(nav.nextItem())
        .isEqualTo(new PublicPostSeriesNav.ItemLink("POST", "part-3", null, "Part 3"));
  }

  @Test
  void seriesNavIsLeftOutWhenThePostIsNotReadableInItsSeries() {
    UserEntity author = authorWithUsername("john");
    publishedSeriesPost(author, 2L);
    when(seriesItemReader.readableEntries(5L))
        .thenReturn(List.of(new SeriesEntry(SeriesItemType.NOTE, 40L, null, "a note", null, null)));

    assertThat(service.findPublicPost("john", "part-2", PostReader.of(null), null).body().series())
        .isNull();
  }

  @Test
  void findDraftReturnsNotFound() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "draft-post", "Draft", "ko");
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "draft-post", null))
        .thenReturn(Optional.of(post));

    assertThatThrownBy(
            () -> service.findPublicPost("john", "draft-post", PostReader.of(null), null).body())
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void previewReturnsDraftPostBypassingStatusGuard() {
    UserEntity author = authorWithUsername("john");
    PostEntity post = new PostEntity(author.getId(), "draft-post", "Draft", "ko");
    post.ensurePreviewToken("tok-123");
    when(postRepository.findByPreviewToken("tok-123")).thenReturn(Optional.of(post));
    when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
    PostBlockEntity b1 = new PostBlockEntity(post.getId(), PostBlockType.PARAGRAPH, "Hello", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(b1));

    PublicPostDetail detail = service.findPreviewPost("tok-123", PostReader.of(null)).body();

    assertThat(detail.author().username()).isEqualTo("john");
    assertThat(detail.post().slug()).isEqualTo("draft-post");
    assertThat(detail.blocks()).hasSize(1);
  }

  @Test
  void previewUnknownTokenReturnsNotFound() {
    when(postRepository.findByPreviewToken("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findPreviewPost("nope", PostReader.of(null)).body())
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void previewBlankTokenReturnsNotFound() {
    assertThatThrownBy(() -> service.findPreviewPost("  ", PostReader.of(null)).body())
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void previewDeletedAuthorReturnsNotFound() {
    UserEntity author = authorWithUsername("john");
    author.softDelete();
    PostEntity post = new PostEntity(author.getId(), "draft-post", "Draft", "ko");
    post.ensurePreviewToken("tok-123");
    when(postRepository.findByPreviewToken("tok-123")).thenReturn(Optional.of(post));
    when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));

    assertThatThrownBy(() -> service.findPreviewPost("tok-123", PostReader.of(null)).body())
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @ParameterizedTest
  @EnumSource(PreviewState.class)
  void previewShowsOnlyDraftsTheAuthorMayStillShareAndPublishedPosts(PreviewState state) {
    UserEntity author = authorWithUsername("john");
    PostEntity post = state.post(author.getId());
    post.ensurePreviewToken("tok-123");
    when(postRepository.findByPreviewToken("tok-123")).thenReturn(Optional.of(post));
    when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
    if (state.suspendsAuthor) author.suspend(NOW.plusSeconds(3600));

    if (state.served) {
      assertThat(service.findPreviewPost("tok-123", PostReader.of(null)).body().post().slug())
          .isEqualTo("preview-post");
    } else {
      assertThatThrownBy(() -> service.findPreviewPost("tok-123", PostReader.of(null)).body())
          .isInstanceOf(PostException.class)
          .extracting(e -> ((PostException) e).errorCode())
          .isEqualTo(PostErrorCode.POST_NOT_FOUND);
    }
  }

  enum PreviewState {
    SCHEDULED(true, false),
    PUBLISHED(true, false),
    PUBLISHED_BY_SUSPENDED_AUTHOR(true, true),
    UNPUBLISHED(false, false),
    TAKEN_DOWN_DRAFT(false, false),
    TAKEN_DOWN_AFTER_PUBLISHING(false, false),
    DRAFT_OF_SUSPENDED_AUTHOR(false, true);

    final boolean served;
    final boolean suspendsAuthor;

    PreviewState(boolean served, boolean suspendsAuthor) {
      this.served = served;
      this.suspendsAuthor = suspendsAuthor;
    }

    PostEntity post(Long authorId) {
      PostEntity post = new PostEntity(authorId, "preview-post", "Preview", "ko");
      switch (this) {
        case SCHEDULED -> post.schedule(Instant.now().plusSeconds(3600));
        case PUBLISHED, PUBLISHED_BY_SUSPENDED_AUTHOR -> post.publish();
        case UNPUBLISHED -> {
          post.publish();
          post.unpublish();
        }
        case TAKEN_DOWN_DRAFT -> post.takeDown(NOW);
        case TAKEN_DOWN_AFTER_PUBLISHING -> {
          post.publish();
          post.takeDown(NOW);
        }
        case DRAFT_OF_SUSPENDED_AUTHOR -> {}
      }
      return post;
    }
  }

  @Test
  void findUnpublishedReturnsGone() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "gone-post", "Gone", "ko");
    post.publish();
    post.unpublish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "gone-post", null))
        .thenReturn(Optional.of(post));

    assertThatThrownBy(
            () -> service.findPublicPost("john", "gone-post", PostReader.of(null), null).body())
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_GONE);
  }

  @Test
  void findHydratesCtaBlocks() {
    UserEntity author = john();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "p", null))
        .thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "{\"ctaId\":42}", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));
    CtaEntity cta =
        new CtaEntity(
            author.getId(),
            "30 min consult",
            "https://cal.com/me",
            CtaStyle.PRIMARY,
            CtaPurpose.BOOKING);
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(cta));

    PublicPostDetail detail = service.findPublicPost("john", "p", PostReader.of(null), null).body();

    assertThat(detail.blocks()).hasSize(1);
    PublicPostBlockView block = detail.blocks().get(0);
    assertThat(block.type()).isEqualTo("CTA_REF");
    assertThat(block.cta()).isNotNull();
    assertThat(block.cta().label()).isEqualTo("30 min consult");
    assertThat(block.cta().url()).isEqualTo("https://cal.com/me");
    assertThat(block.cta().style()).isEqualTo("PRIMARY");
    assertThat(block.cta().purpose()).isEqualTo("BOOKING");
    assertThat(block.cta().deleted()).isFalse();
  }

  @Test
  void servesTrackedShortLinkUrlWhenCtaHasTracking() {
    UserEntity author = john();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "p", null))
        .thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "{\"ctaId\":42}", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));
    CtaEntity cta =
        new CtaEntity(
            author.getId(),
            "Join",
            "https://example.com/join",
            CtaStyle.PRIMARY,
            CtaPurpose.CUSTOM);
    cta.trackVia("xy12ab");
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(cta));

    PublicPostDetail detail = service.findPublicPost("john", "p", PostReader.of(null), null).body();

    // Public response serves the tracked short link (not the raw external url) so clicks are
    // measured.
    assertThat(detail.blocks().get(0).cta().url()).isEqualTo("https://kurl.me/xy12ab");
  }

  @Test
  void findHandlesDeletedCta() {
    UserEntity author = john();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "p", null))
        .thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "{\"ctaId\":42}", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));
    CtaEntity cta =
        new CtaEntity(author.getId(), "Gone", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    cta.softDelete();
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(cta));

    PublicPostDetail detail = service.findPublicPost("john", "p", PostReader.of(null), null).body();

    assertThat(detail.blocks().get(0).cta()).isNotNull();
    assertThat(detail.blocks().get(0).cta().deleted()).isTrue();
  }

  @Test
  void anotherAuthorsCtaComesBackAsAnEmptyReference() {
    UserEntity author = john();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "p", null))
        .thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "{\"ctaId\":42}", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));
    CtaEntity someoneElses =
        new CtaEntity(8L, "Theirs", "https://their.site", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    someoneElses.trackVia("th31rs");
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(someoneElses));

    PublicPostDetail detail = service.findPublicPost("john", "p", PostReader.of(null), null).body();

    assertThat(detail.blocks())
        .singleElement()
        .satisfies(
            block -> {
              assertThat(block.type()).isEqualTo("CTA_REF");
              assertThat(block.content()).isEqualTo("{\"ctaId\":42}");
              assertThat(block.cta()).isNull();
            });
  }

  @Test
  void findHandlesMissingCtaGracefully() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "p", null))
        .thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "{\"ctaId\":99}", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));
    when(ctaRepository.findById(99L)).thenReturn(Optional.empty());

    PublicPostDetail detail = service.findPublicPost("john", "p", PostReader.of(null), null).body();

    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).cta()).isNull();
  }

  @Test
  void findHandlesMalformedCtaContent() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "p", null))
        .thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "not-json", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));

    PublicPostDetail detail = service.findPublicPost("john", "p", PostReader.of(null), null).body();

    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).cta()).isNull();
  }

  @Test
  void findNonExistentSlugReturnsNotFound() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "nope", null))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.findPublicPost("john", "nope", PostReader.of(null), null).body())
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void pinnedPostsSurfaceFirstThenPublishedOrderPreserved() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity newest = new PostEntity(author.getId(), "newest", "Newest", "ko");
    newest.publish();
    PostEntity pinned = new PostEntity(author.getId(), "pinned", "Pinned", "ko");
    pinned.publish();
    pinned.pinAt(0);
    PostEntity oldest = new PostEntity(author.getId(), "oldest", "Oldest", "ko");
    oldest.publish();
    when(postRepository.findProfilePosts(author.getId(), null))
        .thenReturn(List.of(newest, pinned, oldest));

    PublicPostListView response = service.listPublicPosts("john", PostReader.of(null)).body();

    assertThat(response.posts())
        .extracting(PublicPostListItem::slug)
        .containsExactly("pinned", "newest", "oldest");
    assertThat(response.posts())
        .extracting(PublicPostListItem::pinned)
        .containsExactly(true, false, false);
  }

  private PostEntity protectedPost(UserEntity author, String slug, PostAudience audience) {
    PostEntity post =
        com.example.short_link.support.TestEntities.withId(
            new PostEntity(author.getId(), slug, "Title " + slug, "ko"), 42L);
    post.updateExcerpt("the excerpt");
    post.updateTags(List.of("tag"));
    post.publish();
    post.changeAudience(audience, null, "the-key-of-" + slug);
    lenient().when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    lenient()
        .when(postRepository.findUnblockedByUserIdAndSlug(eq(author.getId()), eq(slug), any()))
        .thenReturn(Optional.of(post));
    return post;
  }

  private UserEntity john() {
    return com.example.short_link.support.TestEntities.withId(authorWithUsername("john"), 7L);
  }

  @Test
  void aStrangerSeesAFollowersPostsTitleAndOneNoticeInTheirLanguage() {
    protectedPost(john(), "f", PostAudience.FOLLOWERS);

    PostScoped<PublicPostDetail> scoped =
        service.findPublicPost("john", "f", PostReader.of(9L), "en-US,en;q=0.8");

    PublicPostListItem post = scoped.body().post();
    assertThat(scoped.restricted()).isTrue();
    assertThat(post.title()).isEqualTo("Title f");
    assertThat(post.locked().reason()).isEqualTo(PostLock.FOLLOWERS);
    assertThat(post.excerpt()).isNull();
    assertThat(post.tags()).isEmpty();
    assertThat(post.lastEditedAt()).isNull();
    assertThat(post.hasPassword()).isNull();
    assertThat(scoped.body().series()).isNull();
    assertThat(scoped.body().blocks())
        .singleElement()
        .satisfies(
            block -> {
              assertThat(block.type()).isEqualTo("PARAGRAPH");
              assertThat(block.content()).isEqualTo("Only followers can read this post.");
            });
    verifyNoInteractions(postBlockRepository);
  }

  @Test
  void aReaderInABlockedCountrySeesTheCountryNoticeEvenWithTheRightKey() {
    PublicPostQueryService fromJapan =
        new PublicPostQueryService(
            userRepository,
            postRepository,
            postBlockRepository,
            seriesRepository,
            seriesItemReader,
            ctaRepository,
            new com.example.short_link.link.application.ShortLinkUrlBuilder("https://kurl.me"),
            userBlocks,
            PostAccessFixtures.policy(mock(FollowRepository.class), "JP"),
            PostAccessFixtures.unlistedUrls(),
            PostAccessFixtures.rooms(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    PostEntity post = protectedPost(john(), "u", PostAudience.UNLISTED);
    post.blockCountries(List.of("JP"));

    PublicPostDetail detail =
        fromJapan
            .findPublicPost(
                "john", "u", new PostReader(null, "the-key-of-u", null, "1.2.3.4", false), null)
            .body();

    assertThat(detail.post().locked().reason()).isEqualTo(PostLock.REGION);
    assertThat(detail.post().regionRestricted()).isTrue();
    assertThat(detail.post().blockedCountries()).isNull();
    assertThat(detail.blocks().get(0).content()).isEqualTo("이 나라에서는 볼 수 없는 글이에요");
  }

  @Test
  void anUnlistedPostWithoutItsKeyIsNotFoundEvenOnceUnpublished() {
    PostEntity post = protectedPost(john(), "u", PostAudience.UNLISTED);

    assertThatThrownBy(() -> service.findPublicPost("john", "u", PostReader.of(9L), null))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));
    post.unpublish();
    assertThatThrownBy(() -> service.findPublicPost("john", "u", PostReader.of(9L), null))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));
    assertThatThrownBy(
            () ->
                service.findPublicPost(
                    "john", "u", new PostReader(null, "the-key-of-u", null, null, false), null))
        .isInstanceOfSatisfying(
            PostException.class, e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_GONE));
  }

  @Test
  void theAuthorReadsTheirOwnUnlistedPostWithItsShareableLink() {
    protectedPost(john(), "u", PostAudience.UNLISTED);

    PublicPostListItem post =
        service.findPublicPost("john", "u", PostReader.of(7L), null).body().post();

    assertThat(post.locked()).isNull();
    assertThat(post.excerpt()).isEqualTo("the excerpt");
    assertThat(post.unlistedUrl()).isEqualTo("https://blog.kurl.me/@john/u?k=the-key-of-u");
    assertThat(post.hasPassword()).isFalse();
    assertThat(post.blockedCountries()).isEmpty();
  }

  @Test
  void markdownOfALockedPostIsRefusedWithItsReason() {
    protectedPost(john(), "p", PostAudience.PASSWORD);

    assertThatThrownBy(() -> service.findReadablePost("john", "p", PostReader.of(9L)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_LOCKED);
              assertThat(e.properties()).containsEntry("reason", PostLock.PASSWORD);
            });
  }

  @Test
  void aPublishedProtectedPostPreviewsAsAnAnonymousReader() {
    UserEntity author = john();
    PostEntity post = protectedPost(author, "p", PostAudience.FOLLOWERS);
    post.ensurePreviewToken("tok-protected");
    when(postRepository.findByPreviewToken("tok-protected")).thenReturn(Optional.of(post));
    when(userRepository.findById(7L)).thenReturn(Optional.of(author));

    PostScoped<PublicPostDetail> preview =
        service.findPreviewPost("tok-protected", PostReader.of(7L));

    assertThat(preview.body().post().locked().reason()).isEqualTo(PostLock.FOLLOWERS);
    assertThat(preview.restricted()).isTrue();
  }
}
