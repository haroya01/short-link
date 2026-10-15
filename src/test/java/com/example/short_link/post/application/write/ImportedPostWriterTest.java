package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SourceUrlHash;
import com.example.short_link.post.domain.repository.ImportedPostRepository;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ImportedPostWriterTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
  private static final String URL = "https://velog.io/@writer/a";

  private final PostRepository posts = mock(PostRepository.class);
  private final ImportedPostRepository imported = mock(ImportedPostRepository.class);
  private final PostBlockRepository blocks = mock(PostBlockRepository.class);
  private final UserModerationGuard moderation = mock(UserModerationGuard.class);
  private final PostNumbering numbering = mock(PostNumbering.class);
  private final PostSearchTextUpdater searchText = mock(PostSearchTextUpdater.class);
  private final PostRevisionCapture revisions = mock(PostRevisionCapture.class);
  private final PostNoteQuotes noteQuotes = mock(PostNoteQuotes.class);
  private final ProfileCacheInvalidator cache = mock(ProfileCacheInvalidator.class);
  private final DeletePostUseCase deletePost = mock(DeletePostUseCase.class);
  private final ImportedPostWriter writer =
      new ImportedPostWriter(
          posts,
          imported,
          blocks,
          moderation,
          numbering,
          searchText,
          revisions,
          noteQuotes,
          cache,
          deletePost,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    when(posts.save(any()))
        .thenAnswer(
            i -> {
              PostEntity post = i.getArgument(0);
              if (post.getId() == null) {
                ReflectionTestUtils.setField(post, "id", 11L);
              }
              return post;
            });
  }

  private static PostEntity draft(Long owner) {
    PostEntity post = PostEntity.importedDraft(owner, "draft-abc1234", 3L, "VELOG", URL);
    ReflectionTestUtils.setField(post, "id", 11L);
    return post;
  }

  private static PublishImportedPostCommand publish(
      Long postId, String coverUrl, Instant original) {
    return new PublishImportedPostCommand(
        7L,
        postId,
        "제목",
        "요약",
        "ko",
        List.of("tag"),
        List.of(new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, "본문")),
        coverUrl,
        coverUrl == null ? null : "post-images/k",
        true,
        true,
        55L,
        "abc123",
        true,
        original);
  }

  @Test
  void beginningAnImportOpensADraftWithItsOrigin() {
    Optional<Long> id = writer.begin(new BeginImportedPostCommand(7L, 3L, "VELOG", URL, null));

    assertThat(id).contains(11L);
    ArgumentCaptor<PostEntity> saved = ArgumentCaptor.forClass(PostEntity.class);
    verify(posts).save(saved.capture());
    PostEntity draft = saved.getValue();
    assertThat(draft.isDraft()).isTrue();
    assertThat(draft.getSlug()).matches("draft-[0-9a-z]{7}");
    assertThat(draft.getImportSourceId()).isEqualTo(3L);
    assertThat(draft.getSourcePlatform()).isEqualTo("VELOG");
    assertThat(draft.getSourceUrlHash()).isEqualTo(SourceUrlHash.of(URL));
    verify(posts).flush();
    verify(moderation).requireCanWrite(7L);
  }

  @Test
  void anInterruptedImportResumesItsOwnDraftOnly() {
    when(posts.findById(11L)).thenReturn(Optional.of(draft(7L)));
    assertThat(writer.begin(new BeginImportedPostCommand(7L, 3L, "VELOG", URL, 11L))).contains(11L);
    verify(posts, never()).save(any());

    assertThat(writer.begin(new BeginImportedPostCommand(8L, 3L, "VELOG", URL, 11L))).isEmpty();

    PostEntity published = draft(7L);
    published.updateTitle("t");
    published.publishImported(false, null, null, false, NOW, NOW);
    when(posts.findById(11L)).thenReturn(Optional.of(published));
    assertThat(writer.begin(new BeginImportedPostCommand(7L, 3L, "VELOG", URL, 11L))).isEmpty();

    when(posts.findById(11L)).thenReturn(Optional.empty());
    assertThat(writer.begin(new BeginImportedPostCommand(7L, 3L, "VELOG", URL, 11L))).contains(11L);
  }

  @Test
  void publishingFillsTheDraftAndKeepsTheOriginalDate() {
    PostEntity draft = draft(7L);
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(draft));
    List<PostBlockEntity> persisted =
        List.of(new PostBlockEntity(11L, PostBlockType.PARAGRAPH, "본문", 0));
    when(blocks.findAllByPostIdOrderByBlockOrderAsc(11L)).thenReturn(persisted);
    Instant original = Instant.parse("2022-12-31T18:32:39Z");

    assertThat(writer.publish(publish(11L, "https://img.kurl.me/a.png", original))).isTrue();

    assertThat(draft.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(draft.getPublishedAt()).isEqualTo(original);
    assertThat(draft.getTitle()).isEqualTo("제목");
    assertThat(draft.getExcerpt()).isEqualTo("요약");
    assertThat(draft.getTags()).containsExactly("tag");
    assertThat(draft.thumbnailUrl()).isEqualTo("https://img.kurl.me/a.png");
    assertThat(draft.isSourceExcerptOnly()).isTrue();
    assertThat(draft.getSourceLinkId()).isEqualTo(55L);
    assertThat(draft.getSourceShortCode()).isEqualTo("abc123");
    assertThat(draft.isHiddenFromDiscover()).isTrue();
    assertThat(draft.isDiscoverable()).isFalse();
    verify(numbering).numberIfClientMade(draft);
    verify(blocks).deleteAllByPostId(11L);
    verify(blocks).insertAll(anyList());
    verify(searchText).refresh(draft, persisted);
    verify(revisions).capture(draft, persisted);
    verify(noteQuotes).indexFirstPublish(draft, persisted);
    verify(cache).evictByUserId(7L);
  }

  @Test
  void aFutureOrMissingOriginalDateBecomesNow() {
    PostEntity draft = draft(7L);
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(draft));

    writer.publish(publish(11L, null, NOW.plusSeconds(3600)));

    assertThat(draft.getPublishedAt()).isEqualTo(NOW);
    assertThat(draft.getOgImageUrl()).isNull();

    PostEntity undated = draft(7L);
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(undated));
    writer.publish(
        new PublishImportedPostCommand(
            7L, 11L, "t", null, "ko", null, null, null, null, false, false, null, "x", false,
            null));
    assertThat(undated.getPublishedAt()).isEqualTo(NOW);
    assertThat(undated.getSourceShortCode()).isNull();
    verify(blocks, never()).insertAll(List.of());
  }

  @Test
  void onlyTheOwnersStillDraftImportIsPublished() {
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.empty());
    assertThat(writer.publish(publish(11L, null, NOW))).isFalse();

    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(draft(8L)));
    assertThat(writer.publish(publish(11L, null, NOW))).isFalse();

    PostEntity written = new PostEntity(7L, "mine", "t", "ko");
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(written));
    assertThat(writer.publish(publish(11L, null, NOW))).isFalse();

    PostEntity takenDown = draft(7L);
    takenDown.takeDown(NOW);
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(takenDown));
    assertThat(writer.publish(publish(11L, null, NOW))).isFalse();

    PostEntity live = draft(7L);
    live.updateTitle("t");
    live.publishImported(false, null, null, false, NOW, NOW);
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(live));
    assertThat(writer.publish(publish(11L, null, NOW))).isFalse();
    verify(blocks, never()).insertAll(anyList());
  }

  @Test
  void aSuspendedWriterCannotPublishAnImport() {
    when(posts.findByIdForUpdate(11L)).thenReturn(Optional.of(draft(7L)));
    doThrow(new IllegalStateException("suspended")).when(moderation).requireCanWrite(7L);

    assertThatThrownBy(() -> writer.publish(publish(11L, null, NOW)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void hidingAndDeletingWorkOnEveryPostOfTheSource() {
    when(imported.updateHiddenFromDiscover(3L, true)).thenReturn(4);
    when(imported.findIdsByImportSourceId(3L)).thenReturn(List.of(1L, 2L));

    assertThat(writer.hideFromDiscover(3L, true)).isEqualTo(4);
    assertThat(writer.deleteAllFrom(7L, 3L)).isEqualTo(2);
    verify(deletePost).execute(new DeletePostCommand(7L, 1L));
    verify(deletePost).execute(new DeletePostCommand(7L, 2L));
  }

  @Test
  void commandsRejectWhatTheEditorWouldReject() {
    assertThatThrownBy(() -> new BeginImportedPostCommand(null, 3L, "V", URL, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BeginImportedPostCommand(7L, null, "V", URL, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BeginImportedPostCommand(7L, 3L, " ", URL, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BeginImportedPostCommand(7L, 3L, "V", "x".repeat(2049), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PublishImportedPostCommand(
                    7L, 1L, " ", null, "ko", null, null, null, null, false, false, null, null,
                    false, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PublishImportedPostCommand(
                    7L,
                    1L,
                    "t",
                    "e".repeat(501),
                    "ko",
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    false,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PublishImportedPostCommand(
                    7L, 1L, "t", null, "fr", null, null, null, null, false, false, null, null,
                    false, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PublishImportedPostCommand(
                    null, 1L, "t", null, "ko", null, null, null, null, false, false, null, null,
                    false, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PublishImportedPostCommand(
                    7L, null, "t", null, "ko", null, null, null, null, false, false, null, null,
                    false, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
