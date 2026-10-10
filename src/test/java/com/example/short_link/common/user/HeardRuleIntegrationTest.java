package com.example.short_link.common.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteThreadView;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.HighlightFeedItem;
import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.HighlightView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.read.PostHighlightQueryService;
import com.example.short_link.post.application.read.PostHighlightReplyQueryService;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicFeedItem;
import com.example.short_link.post.application.read.PublicPostListView;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.post.application.read.QuotingPostsQueryService;
import com.example.short_link.post.collection.application.read.DiscoverConnectionView;
import com.example.short_link.post.collection.application.read.DiscoverFeedQueryService;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostNoteQuoteRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// One viewer and six writers: one the viewer hears, one whose mute has ended, one the viewer
// blocked, one who blocked the viewer, one muted for good and one muted for another hour. Lists of
// other people's writing keep the first two; opening a writer directly hides only the blocked two.
// Anonymous sees all six.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HeardRuleIntegrationTest {

  private static final List<String> HEARD = List.of("hr-normal", "hr-expired");
  private static final List<String> BLOCKED = List.of("hr-blocked", "hr-blocker");
  private static final List<String> UNHEARD =
      List.of("hr-blocked", "hr-blocker", "hr-muted", "hr-mutedlater");

  @Autowired private PostCommentQueryService comments;
  @Autowired private PostHighlightQueryService highlights;
  @Autowired private PostHighlightReplyQueryService highlightReplies;
  @Autowired private DiscoverFeedQueryService connections;
  @Autowired private QuotingPostsQueryService quotingPosts;
  @Autowired private PublicPostQueryService profilePosts;
  @Autowired private NoteQueryService notes;
  @Autowired private UserRepository userRepository;
  @Autowired private BlockRepository blockRepository;
  @Autowired private MuteRepository muteRepository;
  @Autowired private FollowRepository followRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private CommentRepository commentRepository;
  @Autowired private PostHighlightRepository highlightRepository;
  @Autowired private PostHighlightReplyRepository replyRepository;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CollectionConnectionRepository connectionRepository;
  @Autowired private PostNoteQuoteRepository noteQuotes;
  @Autowired private NoteRepository noteRepository;
  @Autowired private NoteRepostRepository repostRepository;

  private long viewer;
  private int spans;
  private final Map<String, Long> writers = new LinkedHashMap<>();

  @BeforeEach
  void viewerWhoBlockedMutedAndWasBlocked() {
    viewer = user("hr-viewer");
    for (String handle : all()) {
      writers.put(handle, user(handle));
    }
    Instant now = Instant.now();
    blockRepository.save(new UserBlockEntity(viewer, writer("hr-blocked")));
    blockRepository.save(new UserBlockEntity(writer("hr-blocker"), viewer));
    muteRepository.save(new UserMuteEntity(viewer, writer("hr-muted"), false, null));
    muteRepository.save(
        new UserMuteEntity(viewer, writer("hr-mutedlater"), false, now.plus(1, ChronoUnit.HOURS)));
    muteRepository.save(
        new UserMuteEntity(viewer, writer("hr-expired"), true, now.minus(1, ChronoUnit.HOURS)));
  }

  @Test
  void commentsLeaveOutUnheardWritersAndRepliesUnderThem() {
    long post = publish(writer("hr-normal"), "hr-commented");
    Map<String, Long> tops = new LinkedHashMap<>();
    for (String handle : all()) {
      tops.put(handle, comment(post, writer(handle), null));
    }
    comment(post, writer("hr-normal"), tops.get("hr-blocked"));
    comment(post, writer("hr-expired"), tops.get("hr-normal"));
    comment(post, writer("hr-muted"), tops.get("hr-normal"));

    List<CommentView> signedIn = comments.listForPost(post, viewer, false);
    assertThat(signedIn)
        .extracting(c -> c.author().username())
        .containsExactly("hr-normal", "hr-expired", "hr-expired");
    assertThat(signedIn.get(2).parentId()).isEqualTo(tops.get("hr-normal"));
    assertThat(comments.listForPost(post, null, false)).hasSize(9);
  }

  @Test
  void commentsAnAdminTookDownStayHiddenForEveryone() {
    long post = publish(writer("hr-normal"), "hr-takedown");
    long kept = comment(post, writer("hr-normal"), null);
    long takenDown = comment(post, writer("hr-expired"), null);
    long takenDownReply = comment(post, writer("hr-expired"), kept);
    for (long id : List.of(takenDown, takenDownReply)) {
      CommentEntity c = commentRepository.findById(id).orElseThrow();
      c.softDelete();
      commentRepository.save(c);
    }

    assertThat(comments.listForPost(post, viewer, false))
        .extracting(CommentView::id)
        .containsExactly(kept);
    assertThat(comments.listForPost(post, null, false))
        .extracting(CommentView::id)
        .containsExactly(kept);
  }

  @Test
  void highlightsOnAPostAndTheirRepliesLeaveOutUnheardWriters() {
    long post = publish(writer("hr-normal"), "hr-highlighted");
    for (String handle : all()) {
      highlight(post, writer(handle));
    }
    long heardHighlight = highlight(post, writer("hr-normal"));
    for (String handle : all()) {
      replyRepository.save(new PostHighlightReplyEntity(heardHighlight, writer(handle), "답"));
    }
    long mutedHighlight = highlight(post, writer("hr-muted"));
    replyRepository.save(new PostHighlightReplyEntity(mutedHighlight, writer("hr-normal"), "답"));

    assertThat(usernames(highlights.listForPost(post, viewer), HighlightView::author))
        .containsOnly("hr-normal", "hr-expired");
    assertThat(usernames(highlights.listForPost(post, null), HighlightView::author))
        .containsAll(all());
    assertThat(
            usernames(
                highlightReplies.listForHighlight(heardHighlight, viewer),
                HighlightReplyView::author))
        .containsExactlyInAnyOrderElementsOf(HEARD);
    assertThat(highlightReplies.listForHighlight(heardHighlight, null)).hasSize(6);
    assertThat(highlightReplies.listForHighlight(mutedHighlight, viewer)).isEmpty();
    assertThat(highlightReplies.listForHighlight(mutedHighlight, null)).hasSize(1);
  }

  @Test
  void theHighlightFeedLeavesOutUnheardCuratorsAndHighlightsOnUnheardWritersPosts() {
    long heardPost = publish(writer("hr-normal"), "hr-feed-post");
    for (String handle : all()) {
      highlight(heardPost, writer(handle));
      followRepository.save(new FollowEntity(viewer, writer(handle)));
    }
    long blockerPost = publish(writer("hr-blocker"), "hr-feed-blocker-post");
    highlight(blockerPost, writer("hr-normal"));

    for (boolean global : new boolean[] {false, true}) {
      List<HighlightFeedItem> items = highlights.feed(viewer, 0, 50, global).items();
      assertThat(usernames(items, HighlightFeedItem::curator))
          .containsOnly("hr-normal", "hr-expired");
      assertThat(items).extracting(HighlightFeedItem::postSlug).containsOnly("hr-feed-post");
    }
    assertThat(highlights.feed(writer("hr-normal"), 0, 50, true).items())
        .extracting(HighlightFeedItem::postSlug)
        .contains("hr-feed-blocker-post");
  }

  @Test
  void theFollowingHighlightFeedDropsHighlightsOnAPostItsWriterUnpublished() {
    long post = publish(writer("hr-normal"), "hr-feed-withdrawn");
    highlight(post, writer("hr-expired"));
    followRepository.save(new FollowEntity(viewer, writer("hr-expired")));
    assertThat(highlights.feed(viewer, 0, 50, false).items())
        .extracting(HighlightFeedItem::postSlug)
        .containsExactly("hr-feed-withdrawn");

    PostEntity p = postRepository.findById(post).orElseThrow();
    p.unpublish();
    postRepository.save(p);
    assertThat(highlights.feed(viewer, 0, 50, false).items()).isEmpty();

    p.republish();
    postRepository.save(p);
    assertThat(highlights.feed(viewer, 0, 50, false).items())
        .extracting(HighlightFeedItem::postSlug)
        .containsExactly("hr-feed-withdrawn");
  }

  @Test
  void theConnectionFeedLeavesOutUnheardCuratorsAndWhatUnheardWritersMade() {
    long heardPost = publish(writer("hr-normal"), "hr-connected");
    for (String handle : all()) {
      connect(writer(handle), "모아 둔 글 " + handle, ConnectionBlockType.POST, heardPost);
      followRepository.save(new FollowEntity(viewer, writer(handle)));
    }
    long normal = writer("hr-normal");
    connect(
        normal,
        "차단한 사람의 글",
        ConnectionBlockType.POST,
        publish(writer("hr-blocked"), "hr-blocked-connected"));
    connect(normal, "뮤트한 사람의 노트", ConnectionBlockType.NOTE, note(writer("hr-muted"), null));
    connect(
        normal,
        "뮤트한 사람의 밑줄",
        ConnectionBlockType.HIGHLIGHT,
        highlight(heardPost, writer("hr-mutedlater")));
    connect(
        normal,
        "나를 차단한 사람 글의 밑줄",
        ConnectionBlockType.HIGHLIGHT,
        highlight(publish(writer("hr-blocker"), "hr-blocker-highlighted"), normal));

    for (List<DiscoverConnectionView> items :
        List.of(
            connections.feed(viewer, 0, 50, false).items(),
            connections.feed(viewer, 0, 50, true).items(),
            connections.publicFeed(viewer, 0, 50).items())) {
      assertThat(usernames(items, DiscoverConnectionView::curator))
          .containsOnly("hr-normal", "hr-expired");
      assertThat(items).extracting(DiscoverConnectionView::slug).containsOnly("hr-connected");
    }
    assertThat(connections.publicFeed(null, 0, 50).items()).hasSize(10);
  }

  @Test
  void postsQuotingANoteLeaveOutUnheardWriters() {
    long quoted = note(writer("hr-normal"), null);
    for (String handle : all()) {
      noteQuotes.add(publish(writer(handle), handle + "-quoting"), List.of(quoted));
    }

    assertThat(usernames(quotingPosts.ofNote(quoted, viewer, 0).items(), PublicFeedItem::author))
        .containsExactlyInAnyOrderElementsOf(HEARD);
    assertThat(quotingPosts.ofNote(quoted, null, 0).items()).hasSize(6);
  }

  @Test
  void aProfileListsNothingAcrossABlockButStillListsForAMute() {
    Map<String, Long> ownNotes = new LinkedHashMap<>();
    for (String handle : all()) {
      publish(writer(handle), handle + "-own");
      ownNotes.put(handle, note(writer(handle), null));
      repostRepository.addIfAbsent(ownNotes.get(handle), writer(handle));
    }

    for (String handle : all()) {
      int shown = BLOCKED.contains(handle) ? 0 : 1;
      PublicPostListView posts = profilePosts.listPublicPosts(handle, viewer);
      assertThat(posts.posts()).hasSize(shown);
      assertThat(posts.author().username()).isEqualTo(handle);
      assertThat(posts.blockedByViewer()).isEqualTo(handle.equals("hr-blocked"));
      assertThat(posts.blocksViewer()).isEqualTo(handle.equals("hr-blocker"));
      assertThat(notes.byAuthor(handle, 0, 20, viewer).items()).hasSize(shown);
      assertThat(notes.reposts(handle, 0, 20, viewer).items()).hasSize(shown);

      PublicPostListView anonymous = profilePosts.listPublicPosts(handle, null);
      assertThat(anonymous.posts()).hasSize(1);
      assertThat(anonymous.blockedByViewer()).isFalse();
      assertThat(anonymous.blocksViewer()).isFalse();
      assertThat(notes.byAuthor(handle, 0, 20, null).items()).hasSize(1);
      assertThat(notes.reposts(handle, 0, 20, null).items()).hasSize(1);
    }
  }

  @Test
  void someoneElsesRepostsTabLeavesOutMutedAndBlockedWritersNotes() {
    long normal = writer("hr-normal");
    Map<String, Long> reposted = new LinkedHashMap<>();
    for (String handle : all()) {
      reposted.put(handle, note(writer(handle), null));
    }
    for (long noteId : reposted.values()) {
      repostRepository.addIfAbsent(noteId, normal);
    }

    assertThat(notes.reposts("hr-normal", 0, 20, viewer).items())
        .extracting(NoteView::id)
        .containsExactlyInAnyOrder(reposted.get("hr-normal"), reposted.get("hr-expired"));
    assertThat(notes.reposts("hr-normal", 0, 20, null).items()).hasSize(6);
  }

  @Test
  void aBlockedWritersPostIsNotFoundButAMutedWritersPostOpens() {
    for (String handle : all()) {
      String slug = handle + "-detail";
      publish(writer(handle), slug);
      if (BLOCKED.contains(handle)) {
        assertThatThrownBy(() -> profilePosts.findPublicPost(handle, slug, viewer))
            .isInstanceOf(PostException.class)
            .extracting(e -> ((PostException) e).errorCode())
            .isEqualTo(PostErrorCode.POST_NOT_FOUND);
      } else {
        assertThat(profilePosts.findPublicPost(handle, slug, viewer).post().slug()).isEqualTo(slug);
      }
      assertThat(profilePosts.findPublicPost(handle, slug, null).post().slug()).isEqualTo(slug);
    }
  }

  @Test
  void aThreadAcrossABlockIsNotFoundWhileAMutedWritersNoteAndParentStay() {
    for (String handle : all()) {
      long id = note(writer(handle), null);
      if (BLOCKED.contains(handle)) {
        assertThatThrownBy(() -> notes.thread(id, viewer))
            .isInstanceOf(NoteException.class)
            .extracting(e -> ((NoteException) e).errorCode())
            .isEqualTo(NoteErrorCode.NOTE_NOT_FOUND);
      } else {
        assertThat(notes.thread(id, viewer).note().id()).isEqualTo(id);
      }
      assertThat(notes.thread(id, null).note().id()).isEqualTo(id);
    }

    long blockerParent = note(writer("hr-blocker"), null);
    long underBlocker = note(writer("hr-normal"), blockerParent);
    long mutedParent = note(writer("hr-muted"), null);
    long underMuted = note(writer("hr-normal"), mutedParent);
    long mutedReply = note(writer("hr-mutedlater"), underMuted);

    assertThat(notes.thread(underBlocker, viewer).parent()).isNull();
    assertThat(notes.thread(underBlocker, null).parent().id()).isEqualTo(blockerParent);
    NoteThreadView signedIn = notes.thread(underMuted, viewer);
    assertThat(signedIn.parent().id()).isEqualTo(mutedParent);
    assertThat(signedIn.replies()).isEmpty();
    assertThat(notes.thread(underMuted, null).replies())
        .extracting(NoteView::id)
        .containsExactly(mutedReply);
  }

  private long user(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private long writer(String handle) {
    return writers.get(handle);
  }

  private long publish(long author, String slug) {
    PostEntity p = new PostEntity(author, slug, slug, "ko");
    DiscoverableBodies.discoverable(p);
    p.publish();
    return postRepository.save(p).getId();
  }

  private long comment(long post, long author, Long parent) {
    return commentRepository.save(new CommentEntity(post, author, parent, "댓글")).getId();
  }

  private long highlight(long post, long author) {
    int start = spans++;
    return highlightRepository
        .save(new PostHighlightEntity(post, author, 0, 0, start, start + 5, "측정은 늘", null))
        .getId();
  }

  private long note(long author, Long inReplyTo) {
    return noteRepository.save(new NoteEntity(author, "생각을 적어 두는 노트입니다", inReplyTo, null)).getId();
  }

  private void connect(long curator, String title, ConnectionBlockType type, long refId) {
    long collection =
        collectionRepository
            .save(
                new CollectionEntity(
                    curator, title, null, CollectionVisibility.PUBLIC, CollectionKind.COLLECTION))
            .getId();
    connectionRepository.save(new CollectionConnectionEntity(collection, type, refId, null, 0));
  }

  private static <T> List<String> usernames(List<T> items, Function<T, PublicAuthorView> author) {
    return items.stream()
        .map(author)
        .filter(Objects::nonNull)
        .map(PublicAuthorView::username)
        .toList();
  }

  private static List<String> all() {
    List<String> all = new ArrayList<>(HEARD);
    all.addAll(UNHEARD);
    return all;
  }
}
