package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
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
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
            userBlocks);
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
    when(postRepository.findAllByUserIdAndStatusOrderByPublishedAtDesc(
            author.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of(p1, p2));

    PublicPostListView response = service.listPublicPosts("john", null);

    assertThat(response.author().username()).isEqualTo("john");
    assertThat(response.posts()).hasSize(2);
    assertThat(response.posts().get(0).slug()).isEqualTo("post-1");
  }

  @Test
  void listNormalizesUsername() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findAllByUserIdAndStatusOrderByPublishedAtDesc(
            author.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of());

    service.listPublicPosts("  JOHN  ", null);

    org.mockito.Mockito.verify(userRepository).findByUsername("john");
  }

  @Test
  void listUnknownUsernameThrowsProfileNotFound() {
    when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listPublicPosts("unknown", null))
        .isInstanceOf(ProfileException.class)
        .extracting(e -> ((ProfileException) e).errorCode())
        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  void listSoftDeletedUserThrowsProfileNotFound() {
    UserEntity author = authorWithUsername("john");
    author.softDelete();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));

    assertThatThrownBy(() -> service.listPublicPosts("john", null))
        .isInstanceOf(ProfileException.class)
        .extracting(e -> ((ProfileException) e).errorCode())
        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  void aBlockEitherWayListsNoPostsAndSaysWhich() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(userBlocks.between(9L, author.getId())).thenReturn(new BlockRelation(false, true));

    PublicPostListView response = service.listPublicPosts("john", 9L);

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

    PublicPostDetail detail = service.findPublicPost("john", "first-post", null);

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

    PublicPostSeriesNav nav = service.findPublicPost("john", "part-2", null).series();

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

    PublicPostSeriesNav nav = service.findPublicPost("john", "part-2", null).series();

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

    assertThat(service.findPublicPost("john", "part-2", null).series()).isNull();
  }

  @Test
  void findDraftReturnsNotFound() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "draft-post", "Draft", "ko");
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "draft-post", null))
        .thenReturn(Optional.of(post));

    assertThatThrownBy(() -> service.findPublicPost("john", "draft-post", null))
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

    PublicPostDetail detail = service.findPreviewPost("tok-123");

    assertThat(detail.author().username()).isEqualTo("john");
    assertThat(detail.post().slug()).isEqualTo("draft-post");
    assertThat(detail.blocks()).hasSize(1);
  }

  @Test
  void previewUnknownTokenReturnsNotFound() {
    when(postRepository.findByPreviewToken("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findPreviewPost("nope"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void previewBlankTokenReturnsNotFound() {
    assertThatThrownBy(() -> service.findPreviewPost("  "))
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

    assertThatThrownBy(() -> service.findPreviewPost("tok-123"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
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

    assertThatThrownBy(() -> service.findPublicPost("john", "gone-post", null))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_GONE);
  }

  @Test
  void findHydratesCtaBlocks() {
    UserEntity author = authorWithUsername("john");
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

    PublicPostDetail detail = service.findPublicPost("john", "p", null);

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
    UserEntity author = authorWithUsername("john");
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

    PublicPostDetail detail = service.findPublicPost("john", "p", null);

    // Public response serves the tracked short link (not the raw external url) so clicks are
    // measured.
    assertThat(detail.blocks().get(0).cta().url()).isEqualTo("https://kurl.me/xy12ab");
  }

  @Test
  void findHandlesDeletedCta() {
    UserEntity author = authorWithUsername("john");
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

    PublicPostDetail detail = service.findPublicPost("john", "p", null);

    assertThat(detail.blocks().get(0).cta()).isNotNull();
    assertThat(detail.blocks().get(0).cta().deleted()).isTrue();
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

    PublicPostDetail detail = service.findPublicPost("john", "p", null);

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

    PublicPostDetail detail = service.findPublicPost("john", "p", null);

    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).cta()).isNull();
  }

  @Test
  void findNonExistentSlugReturnsNotFound() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findUnblockedByUserIdAndSlug(author.getId(), "nope", null))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findPublicPost("john", "nope", null))
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
    when(postRepository.findAllByUserIdAndStatusOrderByPublishedAtDesc(
            author.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of(newest, pinned, oldest));

    PublicPostListView response = service.listPublicPosts("john", null);

    assertThat(response.posts())
        .extracting(PublicPostListItem::slug)
        .containsExactly("pinned", "newest", "oldest");
    assertThat(response.posts())
        .extracting(PublicPostListItem::pinned)
        .containsExactly(true, false, false);
  }
}
