package com.example.short_link.post.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.short_link.common.post.CommentModerationPort;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.write.DeleteCommentCommand;
import com.example.short_link.post.application.write.DeleteCommentUseCase;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentLikeRepository;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
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
class CommentTombstoneIntegrationTest {

  @Autowired private DeleteCommentUseCase deleteComment;
  @Autowired private CommentModerationPort moderation;
  @Autowired private PostCommentQueryService comments;
  @Autowired private CommentRepository commentRepository;
  @Autowired private CommentLikeRepository likeRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private BlockRepository blockRepository;
  @Autowired private MuteRepository muteRepository;
  @PersistenceContext private EntityManager em;

  private long owner;
  private long alice;
  private long bob;
  private long carol;
  private long post;

  @BeforeEach
  void aPostWithReaders() {
    owner = user("ct-owner");
    alice = user("ct-alice");
    bob = user("ct-bob");
    carol = user("ct-carol");
    post = publish(owner);
  }

  @Test
  void aCommentWithoutRepliesGoesAndOneWithRepliesLeavesAnEmptyPlaceAboveThem() {
    long lone = comment(alice, null, "혼자 쓴 댓글");
    long answered = comment(bob, null, "답글이 달린 댓글");
    long reply = comment(carol, answered, "남의 답글");
    likeRepository.insertIgnore(answered, alice);

    deleteComment.execute(new DeleteCommentCommand(alice, lone));
    deleteComment.execute(new DeleteCommentCommand(bob, answered));
    flush();

    assertThat(commentRepository.findById(lone)).isEmpty();
    CommentEntity place = commentRepository.findById(answered).orElseThrow();
    assertThat(place.isDeleted()).isTrue();
    assertThat(place.getBody()).isEmpty();
    assertThat(commentRepository.findById(reply)).isPresent();

    List<CommentView> listed = comments.listForPost(post, null, true);
    assertThat(listed).extracting(CommentView::id).containsExactly(answered, reply);
    CommentView tombstone = listed.get(0);
    assertThat(tombstone.deleted()).isTrue();
    assertThat(tombstone.author()).isNull();
    assertThat(tombstone.body()).isNull();
    assertThat(tombstone.likeCount()).isZero();
    assertThat(tombstone.mentions()).isEmpty();
    assertThat(tombstone.createdAt()).isNotNull();
    assertThat(listed.get(1).deleted()).isFalse();
    assertThat(listed.get(1).parentId()).isEqualTo(answered);
    assertThat(listed.get(1).body()).isEqualTo("남의 답글");
  }

  @Test
  void thePostOwnerDeletingSomeonesAnsweredCommentLeavesThePlaceToo() {
    long answered = comment(bob, null, "답글이 달린 댓글");
    long reply = comment(carol, answered, "답글");

    deleteComment.execute(new DeleteCommentCommand(owner, answered));
    flush();

    assertThat(comments.listForPost(post, null, true))
        .extracting(CommentView::id, CommentView::deleted)
        .containsExactly(tuple(answered, true), tuple(reply, false));
  }

  @Test
  void theLastReplyGoingTakesTheEmptyPlaceOutOfTheList() {
    long answered = comment(bob, null, "답글이 달린 댓글");
    long first = comment(carol, answered, "첫 답글");
    long second = comment(alice, answered, "둘째 답글");
    deleteComment.execute(new DeleteCommentCommand(bob, answered));

    deleteComment.execute(new DeleteCommentCommand(carol, first));
    flush();
    assertThat(comments.listForPost(post, null, true))
        .extracting(CommentView::id)
        .containsExactly(answered, second);

    deleteComment.execute(new DeleteCommentCommand(alice, second));
    flush();
    assertThat(comments.listForPost(post, null, true)).isEmpty();
    assertThat(commentRepository.findById(answered)).map(CommentEntity::isDeleted).contains(true);
  }

  @Test
  void anAdminTakeDownReadsTheSameAndKeepsItsTextForReview() {
    long answered = comment(bob, null, "내려진 댓글");
    long reply = comment(carol, answered, "답글");
    long lone = comment(alice, null, "답글 없이 내려진 댓글");
    long other = comment(alice, null, "남은 댓글");
    long takenReply = comment(bob, other, "내려진 답글");

    moderation.softDelete(owner, answered);
    moderation.softDelete(owner, lone);
    moderation.softDelete(owner, takenReply);
    flush();

    List<CommentView> listed = comments.listForPost(post, null, true);
    assertThat(listed).extracting(CommentView::id).containsExactly(answered, reply, other);
    assertThat(listed.get(0).deleted()).isTrue();
    assertThat(listed.get(0).body()).isNull();
    assertThat(commentRepository.findById(answered).map(CommentEntity::getBody)).contains("내려진 댓글");
  }

  @Test
  void aReplyUnderAnEmptyPlaceStaysUnlessItsOwnWriterIsUnheard() {
    long viewer = user("ct-viewer");
    blockRepository.save(new UserBlockEntity(viewer, bob));
    muteRepository.save(new UserMuteEntity(viewer, alice, false, null));
    long byBlocked = comment(bob, null, "차단한 사람의 댓글");
    long heardReply = comment(carol, byBlocked, "들리는 답글");
    long byCarol = comment(carol, null, "다른 댓글");
    long mutedReply = comment(alice, byCarol, "뮤트한 사람의 답글");
    long liveBlocked = comment(bob, null, "지우지 않은 차단한 사람의 댓글");
    long underLiveBlocked = comment(carol, liveBlocked, "그 아래 답글");

    deleteComment.execute(new DeleteCommentCommand(bob, byBlocked));
    deleteComment.execute(new DeleteCommentCommand(carol, byCarol));
    flush();

    assertThat(comments.listForPost(post, viewer, true))
        .extracting(CommentView::id)
        .containsExactly(byBlocked, heardReply);
    assertThat(comments.listForPost(post, null, true))
        .extracting(CommentView::id)
        .containsExactly(byBlocked, heardReply, byCarol, mutedReply, liveBlocked, underLiveBlocked);
  }

  @Test
  void withoutAskingForPlacesTheListHasNoDeletedCommentAndKeepsTheRuleForReplies() {
    long viewer = user("ct-viewer");
    blockRepository.save(new UserBlockEntity(viewer, bob));
    long answered = comment(alice, null, "답글이 달린 댓글");
    long reply = comment(carol, answered, "답글");
    long takenDown = comment(alice, null, "내려진 댓글");
    long byBlocked = comment(bob, null, "차단한 사람의 댓글");
    long underBlocked = comment(carol, byBlocked, "그 아래 답글");

    deleteComment.execute(new DeleteCommentCommand(alice, answered));
    deleteComment.execute(new DeleteCommentCommand(bob, byBlocked));
    moderation.softDelete(owner, takenDown);
    flush();

    List<CommentView> anonymous = comments.listForPost(post, null, false);
    List<CommentView> signedIn = comments.listForPost(post, viewer, false);
    assertThat(anonymous).noneMatch(CommentView::deleted);
    assertThat(signedIn).noneMatch(CommentView::deleted);
    assertThat(anonymous).extracting(CommentView::id).containsExactly(reply, underBlocked);
    assertThat(signedIn).extracting(CommentView::id).containsExactly(reply);
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
        new PostEntity(author, "ct-" + UUID.randomUUID().toString().substring(0, 8), "댓글 자리", "ko");
    DiscoverableBodies.discoverable(p);
    p.publish();
    return postRepository.save(p).getId();
  }

  private long comment(long author, Long parent, String body) {
    return commentRepository.save(new CommentEntity(post, author, parent, body)).getId();
  }
}
