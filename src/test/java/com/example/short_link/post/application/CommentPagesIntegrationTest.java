package com.example.short_link.post.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.user.BlockEdge;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.CommentsView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.write.DeleteCommentCommand;
import com.example.short_link.post.application.write.DeleteCommentUseCase;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentPagesIntegrationTest {

  @Autowired private PostCommentQueryService comments;
  @Autowired private DeleteCommentUseCase deleteComment;
  @Autowired private CommentRepository commentRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private BlockRepository blockRepository;
  @PersistenceContext private EntityManager em;

  private long owner;
  private long alice;
  private long bob;
  private long post;

  @BeforeEach
  void aPostWithReaders() {
    owner = user("cp-owner");
    alice = user("cp-alice");
    bob = user("cp-bob");
    post = publish(owner);
  }

  @Test
  void aPageIsTheOldestTopLevelCommentsWithAllTheirRepliesAndPointsAtTheNext() {
    long first = comment(alice, null, "첫 댓글");
    long second = comment(bob, null, "둘째 댓글");
    long third = comment(alice, null, "셋째 댓글");
    long toFirst = comment(bob, first, "첫 댓글에 답");
    long toThird = comment(bob, third, "셋째 댓글에 답");
    flush();

    CommentsView page = comments.page(post, PostViewer.of(null), false, 0L, 2).body();

    assertThat(page.items()).extracting(CommentView::id).containsExactly(first, second, toFirst);
    assertThat(page.next()).isEqualTo(String.valueOf(second));
    assertThat(page.latest()).isEqualTo(String.valueOf(toThird));

    CommentsView rest =
        comments.page(post, PostViewer.of(null), false, Long.parseLong(page.next()), 2).body();

    assertThat(rest.items()).extracting(CommentView::id).containsExactly(third, toThird);
    assertThat(rest.next()).isNull();
  }

  @Test
  void whatIsNewComesWithTheParentsOfItsRepliesSoTheyHaveSomewhereToGo() {
    long old = comment(alice, null, "예전 댓글");
    long seen = comment(bob, null, "본 댓글");
    long reply = comment(bob, old, "예전 댓글에 새 답");
    long fresh = comment(alice, null, "새 댓글");
    flush();

    CommentsView newer = comments.newer(post, PostViewer.of(null), false, seen, 20).body();

    assertThat(newer.items()).extracting(CommentView::id).containsExactly(old, reply, fresh);
    assertThat(newer.next()).isNull();
    assertThat(newer.latest()).isEqualTo(String.valueOf(fresh));
  }

  @Test
  void aLongRunOfNewCommentsComesInPiecesTheClientFollows() {
    long since = comment(alice, null, "기준");
    long a = comment(bob, null, "하나");
    long b = comment(bob, null, "둘");
    long c = comment(bob, null, "셋");
    flush();

    CommentsView head = comments.newer(post, PostViewer.of(null), false, since, 2).body();
    CommentsView tail =
        comments.newer(post, PostViewer.of(null), false, Long.parseLong(head.next()), 2).body();

    assertThat(head.items()).extracting(CommentView::id).containsExactly(a, b);
    assertThat(tail.items()).extracting(CommentView::id).containsExactly(c);
    assertThat(tail.next()).isNull();
  }

  @Test
  void anEmptiedPlaceHoldsItsRepliesInPagesAndInWhatIsNewOnlyWhenAsked() {
    long answered = comment(alice, null, "답글이 달릴 댓글");
    long reply = comment(bob, answered, "답글");
    deleteComment.execute(new DeleteCommentCommand(alice, answered));
    flush();

    assertThat(comments.page(post, PostViewer.of(null), true, 0L, 20).body().items())
        .extracting(CommentView::id, CommentView::deleted)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(answered, true),
            org.assertj.core.groups.Tuple.tuple(reply, false));
    assertThat(comments.page(post, PostViewer.of(null), false, 0L, 20).body().items()).isEmpty();
    assertThat(comments.newer(post, PostViewer.of(null), true, answered, 20).body().items())
        .extracting(CommentView::id)
        .containsExactly(answered, reply);
    assertThat(comments.newer(post, PostViewer.of(null), false, answered, 20).body().items())
        .extracting(CommentView::id)
        .containsExactly(reply);
  }

  @Test
  void pagesAndWhatIsNewLeaveOutWhoTheViewerBlockedAsTheFullListDoes() {
    long kept = comment(alice, null, "남는 댓글");
    long blocked = comment(bob, null, "막힌 사람 댓글");
    long blockedReply = comment(bob, kept, "막힌 사람 답글");
    blockRepository.save(new UserBlockEntity(BlockEdge.blocker(owner).blocked(bob)));
    flush();

    PostViewer reader = PostViewer.of(owner);
    List<Long> full =
        comments.listForPost(post, reader, false).body().stream().map(CommentView::id).toList();

    assertThat(comments.page(post, reader, false, 0L, 20).body().items())
        .extracting(CommentView::id)
        .containsExactlyElementsOf(full)
        .doesNotContain(blocked, blockedReply);
    assertThat(comments.newer(post, reader, false, 0L, 20).body().items())
        .extracting(CommentView::id)
        .containsExactlyElementsOf(full);
  }

  @Test
  void aPostTheViewerCannotReadAnswersWithAnEmptyPageAsTheListDoes() {
    PostEntity draft = new PostEntity(owner, "cp-draft-" + UUID.randomUUID(), "초안", "ko");
    long hidden = postRepository.save(draft).getId();

    CommentsView page = comments.page(hidden, PostViewer.of(alice), false, 0L, 20).body();

    assertThat(page.items()).isEmpty();
    assertThat(page.next()).isNull();
    assertThat(page.latest()).isEqualTo("0");
  }

  private void flush() {
    em.flush();
    em.clear();
  }

  private long user(String prefix) {
    String handle = prefix + UUID.randomUUID().toString().substring(0, 6);
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private long publish(long author) {
    PostEntity p =
        new PostEntity(author, "cp-" + UUID.randomUUID().toString().substring(0, 8), "페이지", "ko");
    DiscoverableBodies.discoverable(p);
    p.publish();
    return postRepository.save(p).getId();
  }

  private long comment(long author, Long parent, String body) {
    return commentRepository.save(new CommentEntity(post, author, parent, body)).getId();
  }
}
