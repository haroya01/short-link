package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
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

@ExtendWith(MockitoExtension.class)
class PublicPostQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private SeriesRepository seriesRepository;
  @Mock private CtaRepository ctaRepository;

  private PublicPostQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PublicPostQueryService(
            userRepository,
            postRepository,
            postBlockRepository,
            seriesRepository,
            ctaRepository,
            new com.example.short_link.link.application.ShortLinkUrlBuilder("https://kurl.me"));
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

    PublicPostListView response = service.listPublicPosts("john");

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

    service.listPublicPosts("  JOHN  ");

    // 검증: trim + lowercase
    org.mockito.Mockito.verify(userRepository).findByUsername("john");
  }

  @Test
  void listUnknownUsernameThrowsProfileNotFound() {
    when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listPublicPosts("unknown"))
        .isInstanceOf(ProfileException.class)
        .extracting(e -> ((ProfileException) e).errorCode())
        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  void listSoftDeletedUserThrowsProfileNotFound() {
    UserEntity author = authorWithUsername("john");
    author.softDelete();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));

    assertThatThrownBy(() -> service.listPublicPosts("john"))
        .isInstanceOf(ProfileException.class)
        .extracting(e -> ((ProfileException) e).errorCode())
        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND);
  }

  @Test
  void findReturnsPublishedPostWithBlocks() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "first-post", "First", "ko");
    post.publish();
    when(postRepository.findByUserIdAndSlug(author.getId(), "first-post"))
        .thenReturn(Optional.of(post));
    PostBlockEntity b1 = new PostBlockEntity(post.getId(), PostBlockType.PARAGRAPH, "Hello", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(b1));

    PublicPostDetail detail = service.findPublicPost("john", "first-post");

    assertThat(detail.author().username()).isEqualTo("john");
    assertThat(detail.post().slug()).isEqualTo("first-post");
    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).type()).isEqualTo("PARAGRAPH");
  }

  @Test
  void findDraftReturnsNotFound() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "draft-post", "Draft", "ko");
    // status remains DRAFT
    when(postRepository.findByUserIdAndSlug(author.getId(), "draft-post"))
        .thenReturn(Optional.of(post));

    assertThatThrownBy(() -> service.findPublicPost("john", "draft-post"))
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
    when(postRepository.findByUserIdAndSlug(author.getId(), "gone-post"))
        .thenReturn(Optional.of(post));

    assertThatThrownBy(() -> service.findPublicPost("john", "gone-post"))
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
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
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

    PublicPostDetail detail = service.findPublicPost("john", "p");

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
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
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
    cta.trackVia("xy12ab"); // wrapped into a kurl short link
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(cta));

    PublicPostDetail detail = service.findPublicPost("john", "p");

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
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "{\"ctaId\":42}", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));
    CtaEntity cta =
        new CtaEntity(author.getId(), "Gone", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    cta.softDelete();
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(cta));

    PublicPostDetail detail = service.findPublicPost("john", "p");

    assertThat(detail.blocks().get(0).cta()).isNotNull();
    assertThat(detail.blocks().get(0).cta().deleted()).isTrue();
  }

  @Test
  void findHandlesMissingCtaGracefully() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "{\"ctaId\":99}", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));
    when(ctaRepository.findById(99L)).thenReturn(Optional.empty());

    PublicPostDetail detail = service.findPublicPost("john", "p");

    // CTA lookup 실패 — block 은 존재하지만 cta = null. UI placeholder 분기 책임.
    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).cta()).isNull();
  }

  @Test
  void findHandlesMalformedCtaContent() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    PostBlockEntity ctaBlock =
        new PostBlockEntity(post.getId(), PostBlockType.CTA_REF, "not-json", 0);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()))
        .thenReturn(List.of(ctaBlock));

    PublicPostDetail detail = service.findPublicPost("john", "p");

    assertThat(detail.blocks()).hasSize(1);
    assertThat(detail.blocks().get(0).cta()).isNull();
  }

  @Test
  void findNonExistentSlugReturnsNotFound() {
    UserEntity author = authorWithUsername("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findPublicPost("john", "nope"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }
}
