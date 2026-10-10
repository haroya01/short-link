package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.post.application.read.PostExportQueryService;
import com.example.short_link.post.application.read.PostQueryService;
import com.example.short_link.post.application.read.PublicFeedItem;
import com.example.short_link.post.application.read.PublicFeedQuery;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PostModerationIntegrationTest {
  private static final String LONG_BODY = "본문 문장이 충분히 길어서 발견 피드의 최소 길이를 넘는다. ".repeat(6);

  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private PostBlockRepository blocks;
  @Autowired private PostRevisionRepository revisions;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PublishPostUseCase publishPost;
  @Autowired private SchedulePostUseCase schedulePost;
  @Autowired private UnpublishPostUseCase unpublishPost;
  @Autowired private RepublishPostUseCase republishPost;
  @Autowired private BackToDraftPostUseCase backToDraft;
  @Autowired private ReplacePostBlocksUseCase replaceBlocks;
  @Autowired private UpdatePostMetadataUseCase updateMetadata;
  @Autowired private RestorePostRevisionUseCase restoreRevision;
  @Autowired private IssuePreviewTokenUseCase issuePreview;
  @Autowired private DeletePostUseCase deletePost;
  @Autowired private PublishScheduledPostUseCase publishScheduledPost;
  @Autowired private TakeDownPostUseCase takeDown;
  @Autowired private PostQueryService ownPosts;
  @Autowired private PostExportQueryService exportPosts;
  @Autowired private PublicPostQueryService publicPosts;
  @Autowired private PublicFeedQueryService feed;
  @Autowired private NoteCommandService notes;

  private TransactionTemplate transactions;
  private Long authorId;
  private String username;
  private String tag;
  private final List<Long> postIds = new ArrayList<>();
  private final List<Long> noteIds = new ArrayList<>();

  @BeforeEach
  void createAuthor() {
    transactions = new TransactionTemplate(transactionManager);
    String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    username = "mod" + unique;
    tag = "moderation-" + unique;
    authorId =
        transactions.execute(
            status -> {
              UserEntity author = new UserEntity(username + "@x.com", "google", unique);
              author.claimUsername(username);
              return users.save(author).getId();
            });
  }

  @AfterEach
  void cleanup() {
    for (Long noteId : noteIds) notes.delete(authorId, noteId);
    transactions.executeWithoutResult(
        status -> {
          for (Long postId : postIds) {
            revisions.deleteAllByPostId(postId);
            blocks.deleteAllByPostId(postId);
            jdbc.update("delete from post_search_text where post_id = ?", postId);
            jdbc.update("delete from post_note_quote where post_id = ?", postId);
            posts.findById(postId).ifPresent(posts::delete);
          }
          posts.flush();
          users.deleteById(authorId);
        });
  }

  @ParameterizedTest
  @EnumSource(PublicWrite.class)
  void aSuspendedAuthorCannotTakeAnyPublicWriteAction(PublicWrite write) {
    Long postId = prepare(write);
    Map<String, Object> before = postRow(postId);
    Long noteId = write == PublicWrite.NOTE_EDIT ? note("before suspension") : null;
    suspendAuthor();

    assertThatThrownBy(() -> run(write, postId, noteId))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.ACCOUNT_SUSPENDED));

    assertThat(postRow(postId)).isEqualTo(before);
    assertThat(noteBodies())
        .containsExactlyElementsOf(noteId == null ? List.of() : List.of("before suspension"));
  }

  @Test
  void aSuspendedAuthorStillEditsDraftsAndUnpublishedPostsDeletesAndExports() throws Exception {
    Long draft = titledDraft();
    Long unpublished = titledDraft();
    publishPost.execute(new PublishPostCommand(authorId, unpublished));
    unpublishPost.execute(new UnpublishPostCommand(authorId, unpublished));
    suspendAuthor();

    replaceBlocks.execute(body(draft, "draft body"));
    updateMetadata.execute(title(draft, "Draft title"));
    replaceBlocks.execute(body(unpublished, "fixed body"));
    assertThat(exportPosts.export(authorId)).isNotEmpty();
    deletePost.execute(new DeletePostCommand(authorId, draft));

    assertThat(posts.findById(draft)).isEmpty();
    assertThat(posts.findById(unpublished).orElseThrow().isUnpublished()).isTrue();
  }

  @Test
  void theScheduledJobReturnsASuspendedAuthorsScheduleToDraftAndDoesNotRetryIt() {
    Long postId = titledDraft();
    schedulePost.execute(
        new SchedulePostCommand(authorId, postId, Instant.now().plusSeconds(3600)));
    suspendAuthor();
    Instant due = Instant.now().plusSeconds(7200);

    assertThat(publishScheduledPost.execute(postId, due)).isFalse();

    PostEntity stored = posts.findById(postId).orElseThrow();
    assertThat(stored.isDraft()).isTrue();
    assertThat(stored.getScheduledAt()).isNull();
    assertThat(stored.getPublishedAt()).isNull();
    assertThat(posts.findScheduledDueIds(due)).doesNotContain(postId);
  }

  @Test
  void aTakenDownPostLeavesEveryPublicReadAndComesBackOnlyThroughReleaseAndRepublish() {
    Long postId = titledDraft();
    replaceBlocks.execute(body(postId, LONG_BODY));
    String token = issuePreview.issue(authorId, postId);
    publishPost.execute(new PublishPostCommand(authorId, postId));
    assertVisibleToReaders(postId, token);

    takeDown.takeDown(authorId, postId);

    assertHiddenFromReaders(postId, token);
    for (Runnable goPublic :
        List.<Runnable>of(
            () -> republishPost.execute(new RepublishPostCommand(authorId, postId)),
            () -> publishPost.execute(new PublishPostCommand(authorId, postId)),
            () ->
                schedulePost.execute(
                    new SchedulePostCommand(authorId, postId, Instant.now().plusSeconds(3600))))) {
      assertThatThrownBy(goPublic::run)
          .isInstanceOfSatisfying(
              PostException.class,
              e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_TAKEN_DOWN));
    }
    assertThatThrownBy(() -> backToDraft.execute(new BackToDraftPostCommand(authorId, postId)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.BACK_TO_DRAFT_NOT_SCHEDULED));

    replaceBlocks.execute(body(postId, LONG_BODY + "고친 문장."));
    restoreRevision.execute(new RestorePostRevisionCommand(authorId, postId, 1));
    var own = ownPosts.findOwnPost(authorId, postId);
    assertThat(own.status()).isEqualTo("UNPUBLISHED");
    assertThat(own.takenDown()).isTrue();
    assertHiddenFromReaders(postId, token);

    takeDown.release(authorId, postId);

    var released = ownPosts.findOwnPost(authorId, postId);
    assertThat(released.status()).isEqualTo("UNPUBLISHED");
    assertThat(released.takenDown()).isFalse();
    assertHiddenFromReaders(postId, token);

    republishPost.execute(new RepublishPostCommand(authorId, postId));

    assertVisibleToReaders(postId, token);
  }

  @Test
  void takingDownAScheduledPostCancelsTheScheduleAndKeepsItOutOfThePreview() {
    Long postId = titledDraft();
    String token = issuePreview.issue(authorId, postId);
    schedulePost.execute(
        new SchedulePostCommand(authorId, postId, Instant.now().plusSeconds(3600)));

    takeDown.takeDown(authorId, postId);

    PostEntity stored = posts.findById(postId).orElseThrow();
    assertThat(stored.isDraft()).isTrue();
    assertThat(stored.getScheduledAt()).isNull();
    assertThat(stored.isTakenDown()).isTrue();
    assertThat(posts.findScheduledDueIds(Instant.now().plusSeconds(7200))).doesNotContain(postId);
    assertNotFound(() -> publicPosts.findPreviewPost(token));
  }

  private void assertVisibleToReaders(Long postId, String token) {
    String slug = posts.findById(postId).orElseThrow().getSlug();
    assertThat(publicPosts.findPublicPost(username, slug, null).post().id()).isEqualTo(postId);
    assertThat(publicPosts.listPublicPosts(username, null).posts())
        .extracting(p -> p.id())
        .contains(postId);
    assertThat(taggedFeed()).contains(postId);
    assertThat(publicPosts.findPreviewPost(token).post().id()).isEqualTo(postId);
  }

  private void assertHiddenFromReaders(Long postId, String token) {
    String slug = posts.findById(postId).orElseThrow().getSlug();
    assertThatThrownBy(() -> publicPosts.findPublicPost(username, slug, null))
        .isInstanceOfSatisfying(
            PostException.class, e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_GONE));
    assertThat(publicPosts.listPublicPosts(username, null).posts())
        .extracting(p -> p.id())
        .doesNotContain(postId);
    assertThat(taggedFeed()).doesNotContain(postId);
    assertNotFound(() -> publicPosts.findPreviewPost(token));
  }

  private static void assertNotFound(Runnable read) {
    assertThatThrownBy(read::run)
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));
  }

  private List<Long> taggedFeed() {
    return feed.feed(null, PublicFeedQuery.from(null, tag, "recent", null, 0, 50)).items().stream()
        .map(PublicFeedItem::id)
        .toList();
  }

  private Long prepare(PublicWrite write) {
    Long postId = titledDraft();
    switch (write) {
      case REPUBLISH -> {
        publishPost.execute(new PublishPostCommand(authorId, postId));
        unpublishPost.execute(new UnpublishPostCommand(authorId, postId));
      }
      case LIVE_BODY, LIVE_METADATA, LIVE_RESTORE ->
          publishPost.execute(new PublishPostCommand(authorId, postId));
      default -> {}
    }
    return postId;
  }

  private void run(PublicWrite write, Long postId, Long noteId) {
    switch (write) {
      case PUBLISH -> publishPost.execute(new PublishPostCommand(authorId, postId));
      case SCHEDULE ->
          schedulePost.execute(
              new SchedulePostCommand(authorId, postId, Instant.now().plusSeconds(3600)));
      case REPUBLISH -> republishPost.execute(new RepublishPostCommand(authorId, postId));
      case LIVE_BODY -> replaceBlocks.execute(body(postId, "while suspended"));
      case LIVE_METADATA -> updateMetadata.execute(title(postId, "While suspended"));
      case LIVE_RESTORE ->
          restoreRevision.execute(new RestorePostRevisionCommand(authorId, postId, 1));
      case PREVIEW_TOKEN -> issuePreview.issue(authorId, postId);
      case NOTE_CREATE ->
          notes.create(authorId, new NoteDraft("while suspended", List.of(), null, null));
      case NOTE_EDIT -> notes.edit(authorId, noteId, "while suspended", null, null);
    }
  }

  enum PublicWrite {
    PUBLISH,
    SCHEDULE,
    REPUBLISH,
    LIVE_BODY,
    LIVE_METADATA,
    LIVE_RESTORE,
    PREVIEW_TOKEN,
    NOTE_CREATE,
    NOTE_EDIT
  }

  private Long titledDraft() {
    Long id =
        transactions.execute(
            status -> {
              PostEntity post =
                  new PostEntity(authorId, "post-" + postIds.size(), "Moderated post", "ko");
              post.updateTags(List.of(tag));
              return posts.save(post).getId();
            });
    postIds.add(id);
    return id;
  }

  private Long note(String body) {
    Long id = notes.create(authorId, new NoteDraft(body, List.of(), null, null)).id();
    noteIds.add(id);
    return id;
  }

  private List<String> noteBodies() {
    return jdbc.queryForList(
        "select body from note where user_id = ? order by id", String.class, authorId);
  }

  private ReplacePostBlocksCommand body(Long postId, String text) {
    return new ReplacePostBlocksCommand(
        authorId,
        postId,
        List.of(new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, text)),
        null,
        false);
  }

  private UpdatePostMetadataCommand title(Long postId, String title) {
    return new UpdatePostMetadataCommand(
        authorId, postId, title, null, null, null, null, null, null, null, false);
  }

  private Map<String, Object> postRow(Long postId) {
    return jdbc.queryForMap(
        "select status, title, scheduled_at, published_at, preview_token, content_version,"
            + " taken_down_at from posts where id = ?",
        postId);
  }

  private void suspendAuthor() {
    transactions.executeWithoutResult(
        status -> {
          UserEntity author = users.findById(authorId).orElseThrow();
          author.suspend(Instant.now().plusSeconds(3600));
          users.save(author);
        });
  }
}
