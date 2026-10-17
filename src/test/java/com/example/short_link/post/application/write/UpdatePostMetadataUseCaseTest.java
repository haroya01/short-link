package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.image.ImageVariantsRequested;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.access.PostAccessFixtures;
import com.example.short_link.post.application.access.PostPasswords;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UpdatePostMetadataUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private PostSearchTextRepository postSearchTextRepository;
  @Mock private PostRevisionCapture revisionCapture;
  @Mock private UserModerationGuard moderation;

  private UpdatePostMetadataUseCase useCase;

  @BeforeEach
  void setUp() {
    PostSearchTextUpdater searchTextUpdater =
        new PostSearchTextUpdater(
            postBlockRepository, postSearchTextRepository, JsonMapper.builder().build());
    useCase =
        new UpdatePostMetadataUseCase(
            postOwnership,
            new PostEditGuard(revisionCapture, moderation),
            postRepository,
            searchTextUpdater,
            revisionCapture,
            new PostWriteViewAssembler(postRepository, PostAccessFixtures.unlistedUrls()),
            new PostAccessSettings(new PostPasswords()),
            profileCache,
            events);
  }

  @Mock private ProfileCacheInvalidator profileCache;
  @Mock private ApplicationEventPublisher events;

  private UpdatePostMetadataCommand access(String audience, String password, List<String> blocked) {
    return new UpdatePostMetadataCommand(
        7L,
        42L,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        new PostAccessChange(audience, password, blocked));
  }

  @Test
  void anAccessOnlyChangeLeavesTheContentVersionAndRevisionsAlone() {
    PostEntity post = ownedPost();
    post.publish();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView view = useCase.execute(access("followers", null, List.of("jp", "KR")));

    assertThat(view.audience()).isEqualTo("FOLLOWERS");
    assertThat(view.blockedCountries()).containsExactly("JP", "KR");
    assertThat(view.contentVersion()).isZero();
    assertThat(post.getLastEditedAt()).isNull();
    verify(revisionCapture, never()).capture(any());
    verify(moderation).requireCanWrite(7L);
    verify(profileCache).evictByUserId(7L);
  }

  @Test
  void aContentEditCarriesItsAccessChangeAlong() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView view =
        useCase.execute(
            new UpdatePostMetadataCommand(
                7L,
                42L,
                "Retitled",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                new PostAccessChange("UNLISTED", null, null)));

    assertThat(view.title()).isEqualTo("Retitled");
    assertThat(view.audience()).isEqualTo("UNLISTED");
    assertThat(view.contentVersion()).isEqualTo(1);
    assertThat(post.getUnlistedKey()).hasSize(32);
    verify(profileCache, never()).evictByUserId(any());
  }

  @Test
  void aPasswordPostNeedsAPasswordAndKeepsItWhenItOpensUp() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    assertThatThrownBy(() -> useCase.execute(access("PASSWORD", null, null)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_PASSWORD_REQUIRED));

    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    PostView protectedView = useCase.execute(access("PASSWORD", "다섯글자다", null));
    String hash = post.getPasswordHash();
    PostView reopened = useCase.execute(access("PUBLIC", null, null));

    assertThat(protectedView.hasPassword()).isTrue();
    assertThat(hash).startsWith("$2a$10$");
    assertThat(reopened.hasPassword()).isTrue();
    assertThat(post.getPasswordHash()).isEqualTo(hash);
  }

  @Test
  void settingsThatCannotBeStoredAreRefusedBeforeAnythingChanges() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    assertCode(access("FRIENDS", null, null), PostErrorCode.POST_AUDIENCE_INVALID);
    assertCode(access(null, "abc", null), PostErrorCode.POST_PASSWORD_LENGTH);
    assertCode(access(null, "가".repeat(201), null), PostErrorCode.POST_PASSWORD_LENGTH);
    assertCode(
        access(null, null, List.of("JP", "XX")), PostErrorCode.POST_BLOCKED_COUNTRIES_INVALID);
    assertCode(
        access("UNLISTED", "valid-password", List.of("ZZ")),
        PostErrorCode.POST_BLOCKED_COUNTRIES_INVALID);
    assertThat(post.getAudience().name()).isEqualTo("PUBLIC");
    assertThat(post.hasPassword()).isFalse();
    assertThat(post.getUnlistedKey()).isNull();
  }

  private void assertCode(UpdatePostMetadataCommand cmd, PostErrorCode code) {
    assertThatThrownBy(() -> useCase.execute(cmd))
        .isInstanceOfSatisfying(
            PostException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }

  private PostEntity ownedPost() {
    return new PostEntity(7L, "original-slug", "Original", "ko");
  }

  @Test
  void updatesTitleOnly() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView updated =
        useCase.execute(
            new UpdatePostMetadataCommand(
                7L, 42L, "New Title", null, null, null, null, null, null, null, null, false));

    assertThat(updated.title()).isEqualTo("New Title");
    assertThat(updated.slug()).isEqualTo("original-slug");
  }

  @Test
  void updatesExcerptAndClearsWithBlank() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, "Summary text.", null, null, null, null, null, null, false));
    assertThat(post.getExcerpt()).isEqualTo("Summary text.");

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, "", null, null, null, null, null, null, false));
    assertThat(post.getExcerpt()).isNull();
  }

  @Test
  void updatesOgImageAndClearsWithBlank() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L,
            42L,
            null,
            null,
            null,
            "https://cdn/og/1.png",
            "og/1.png",
            null,
            null,
            null,
            null,
            false));
    assertThat(post.getOgImageUrl()).isEqualTo("https://cdn/og/1.png");
    assertThat(post.getOgImageKey()).isEqualTo("og/1.png");

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, null, "", null, null, null, null, null, false));
    assertThat(post.getOgImageUrl()).isNull();
    assertThat(post.getOgImageKey()).isNull();
  }

  private UpdatePostMetadataCommand cover(String url, Boolean chosen) {
    return new UpdatePostMetadataCommand(
        7L, 42L, null, null, null, url, null, chosen, null, null, null, false);
  }

  @Test
  void onlyAChosenCoverBecomesTheThumbnail() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView prefilled = useCase.execute(cover("https://cdn/body-first.png", false));
    assertThat(prefilled.coverChosen()).isFalse();
    assertThat(post.thumbnailUrl()).isNull();

    PostView picked = useCase.execute(cover("https://cdn/picked.png", true));
    assertThat(picked.coverChosen()).isTrue();
    assertThat(post.thumbnailUrl()).isEqualTo("https://cdn/picked.png");

    useCase.execute(cover("", null));
    assertThat(post.isCoverChosen()).isFalse();
    assertThat(post.thumbnailUrl()).isNull();
  }

  @Test
  void aClientThatSendsNoCoverChoiceKeepsTheSameCoverAndLeavesANewOneUnchosen() {
    PostEntity post = ownedPost();
    post.updateOgImage("https://cdn/picked.png", null, true);
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(cover("https://cdn/picked.png", null));
    assertThat(post.isCoverChosen()).isTrue();

    useCase.execute(cover("https://cdn/body-first.png", null));
    assertThat(post.isCoverChosen()).isFalse();

    useCase.execute(cover("https://cdn/body-first.png", null));
    assertThat(post.isCoverChosen()).isFalse();
  }

  @Test
  void aNewCoverStoredHereAsksForItsResizedCopiesOnce() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    String stored = "https://cdn.kurl.me/post-images/7/42/cover.jpg";

    useCase.execute(cover(stored, true));
    useCase.execute(cover(stored, true));
    useCase.execute(cover("https://elsewhere.example/picture.jpg", true));

    verify(events).publishEvent(new ImageVariantsRequested(List.of("post-images/7/42/cover.jpg")));
    verify(events, never()).publishEvent(new ImageVariantsRequested(List.of("picture.jpg")));
    verify(events, times(1)).publishEvent(any(ImageVariantsRequested.class));
  }

  @Test
  void updatesLanguageTag() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, null, null, null, null, "ja", null, null, false));
    assertThat(post.getLanguageTag()).isEqualTo("ja");
  }

  @Test
  void updatesTagsWithNormalization() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L,
            42L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("  Spring ", "spring", "JPA", ""),
            null,
            false));

    assertThat(post.getTags()).containsExactly("Spring", "JPA");
  }

  @Test
  void clearsTagsWithEmptyList() {
    PostEntity post = ownedPost();
    post.updateTags(List.of("a", "b"));
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, null, null, null, null, null, List.of(), null, false));

    assertThat(post.getTags()).isEmpty();
  }

  @Test
  void leavesTagsUnchangedWhenNull() {
    PostEntity post = ownedPost();
    post.updateTags(List.of("keep"));
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, "T", null, null, null, null, null, null, null, null, false));

    assertThat(post.getTags()).containsExactly("keep");
  }

  @Test
  void updatesSlugInDraft() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.existsByUserIdAndSlug(7L, "new-slug")).thenReturn(false);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, "new-slug", null, null, null, null, null, null, null, false));
    assertThat(post.getSlug()).isEqualTo("new-slug");
  }

  @Test
  void rejectsSlugCollision() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.existsByUserIdAndSlug(7L, "taken")).thenReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdatePostMetadataCommand(
                        7L, 42L, null, "taken", null, null, null, null, null, null, null, false)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SLUG_CONFLICT);
  }

  @Test
  void rejectsATypedProfilePageName() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdatePostMetadataCommand(
                        7L, 42L, null, "about", null, null, null, null, null, null, null, false)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.SLUG_RESERVED);
              assertThat(e.properties()).containsEntry("slug", "about");
            });
    assertThat(post.getSlug()).isEqualTo("original-slug");
    verify(postRepository, never()).existsByUserIdAndSlug(any(), any());
  }

  @Test
  void keepsAProfilePageNameThePostAlreadyHas() {
    PostEntity post = new PostEntity(7L, "notes", "Original", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, "Edited", "notes", null, null, null, null, null, null, null, false));

    assertThat(post.getTitle()).isEqualTo("Edited");
  }

  @Test
  void rejectsSlugChangeWhenFrozen() {
    PostEntity post = ownedPost();
    post.publish();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.existsByUserIdAndSlug(7L, "new-slug")).thenReturn(false);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdatePostMetadataCommand(
                        7L,
                        42L,
                        null,
                        "new-slug",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SLUG_FROZEN);
  }

  @Test
  void allowsBlankTitleForDraft() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, "", null, null, null, null, null, null, null, null, false));

    assertThat(post.getTitle()).isEmpty();
  }

  @Test
  void matchingBaseVersionSavesAndAnswersWithTheNextVersion() {
    PostEntity post = postAtVersion(3);
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView updated =
        useCase.execute(
            new UpdatePostMetadataCommand(
                7L, 42L, "Mine", null, null, null, null, null, null, null, 3L, false));

    assertThat(updated.title()).isEqualTo("Mine");
    assertThat(updated.contentVersion()).isEqualTo(4L);
  }

  @Test
  void staleBaseVersionLeavesThePostAsTheOtherSaveLeftIt() {
    PostEntity post = postAtVersion(3);
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdatePostMetadataCommand(
                        7L, 42L, "Mine", null, null, null, null, null, null, null, 2L, false)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_EDIT_CONFLICT);
              assertThat(e.properties()).containsEntry("contentVersion", 3L);
            });

    assertThat(post.getTitle()).isEqualTo("Original");
    assertThat(post.getContentVersion()).isEqualTo(3L);
    verify(postRepository, never()).save(any());
  }

  @Test
  void overwriteKeepsTheTitleItReplacesAsARevision() {
    PostEntity post = postAtVersion(3);
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    List<String> capturedTitles = new ArrayList<>();
    doAnswer(
            inv -> {
              capturedTitles.add(inv.<PostEntity>getArgument(0).getTitle());
              return null;
            })
        .when(revisionCapture)
        .capture(post);

    PostView updated =
        useCase.execute(
            new UpdatePostMetadataCommand(
                7L, 42L, "Mine", null, null, null, null, null, null, null, 1L, true));

    assertThat(capturedTitles).containsExactly("Original");
    assertThat(updated.title()).isEqualTo("Mine");
    assertThat(updated.contentVersion()).isEqualTo(4L);
  }

  @Test
  void adminEditAdvancesTheVersionSoTheAuthorsOlderCopyConflicts() {
    PostEntity post = postAtVersion(3);
    when(postRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(post));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostView updated = useCase.adminExecute(1L, 42L, "Moderated", null);

    assertThat(updated.contentVersion()).isEqualTo(4L);
  }

  private static PostEntity postAtVersion(int version) {
    PostEntity post = new PostEntity(7L, "original-slug", "Original", "ko");
    for (int i = 0; i < version; i++) post.markEdited();
    return post;
  }

  @Test
  void aTitleChangeOnAPublishedPostIsKeptAsAVersion() {
    PostEntity post = ownedPost();
    post.publish();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, "Readers see this", null, null, null, null, null, null, null, null, false));

    verify(revisionCapture).capture(org.mockito.ArgumentMatchers.eq(post), any());
  }

  @Test
  void aCoverChangeOnAPublishedPostIsKeptAsAVersion() {
    PostEntity post = ownedPost();
    post.publish();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, null, null, null, "https://cdn/c.png", null, true, null, null, null, false));

    verify(revisionCapture).capture(post);
  }

  @Test
  void aDraftMetadataSaveAddsNoVersion() {
    PostEntity post = ownedPost();
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdatePostMetadataCommand(
            7L, 42L, "Draft title", null, null, null, null, null, null, null, null, false));

    verify(revisionCapture, never()).capture(any(PostEntity.class));
    verify(revisionCapture, never()).capture(any(PostEntity.class), any());
  }
}
