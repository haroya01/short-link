package com.example.short_link.post.note.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.application.read.NoteFeedView;
import com.example.short_link.post.note.application.read.NoteQueryService;
import com.example.short_link.post.note.application.write.NoteCommandService;
import com.example.short_link.post.note.domain.NoteRow;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 노트 규칙의 본선 — 검증·소유권·멱등 좋아요·작성자 hydrate 를 실제 영속성으로 돈다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoteServiceTest {

  @Autowired private NoteCommandService command;
  @Autowired private NoteQueryService query;
  @Autowired private UserRepository userRepository;

  private Long signUp(String email, String oauthId) {
    return userRepository.save(new UserEntity(email, "google", oauthId)).getId();
  }

  @Test
  void createThenFeedShowsNoteWithAuthor() throws Exception {
    Long author = signUp("note-a@example.com", "g-note-1");

    NoteRow created = command.create(author, "  첫 노트  ");
    assertThat(created.body()).isEqualTo("첫 노트");
    assertThat(created.author().id()).isEqualTo(author);

    NoteFeedView feed = query.feed(0, 20);
    assertThat(feed.items().getFirst().body()).isEqualTo("첫 노트");
    assertThat(feed.items().getFirst().likeCount()).isZero();
    assertThat(feed.hasNext()).isFalse();
  }

  @Test
  void blankOrOversizedBodyRejected() {
    Long author = signUp("note-b@example.com", "g-note-2");

    assertThatThrownBy(() -> command.create(author, null))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.NOTE_BODY_REQUIRED));

    assertThatThrownBy(() -> command.create(author, "   "))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.NOTE_BODY_REQUIRED));

    assertThatThrownBy(() -> command.create(author, "글".repeat(501)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.NOTE_BODY_TOO_LONG));
  }

  @Test
  void feedPaginatesAndClampsInputs() {
    Long author = signUp("note-g@example.com", "g-note-7");
    command.create(author, "하나");
    command.create(author, "둘");
    command.create(author, "셋");

    NoteFeedView first = query.feed(0, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.items().getFirst().body()).isEqualTo("셋");
    assertThat(first.hasNext()).isTrue();

    NoteFeedView second = query.feed(1, 2);
    assertThat(second.items()).hasSize(1);
    assertThat(second.hasNext()).isFalse();

    // 음수 페이지·0 사이즈는 0페이지·1건으로 — 호출자가 무엇을 보내든 쿼리는 항상 유효 범위.
    NoteFeedView clamped = query.feed(-3, 0);
    assertThat(clamped.page()).isZero();
    assertThat(clamped.items()).hasSize(1);
    assertThat(clamped.hasNext()).isTrue();
  }

  @Test
  void likeIsIdempotentAndBatchQueryable() {
    Long author = signUp("note-c@example.com", "g-note-3");
    Long fan = signUp("note-d@example.com", "g-note-4");
    Long noteId = command.create(author, "좋아요 대상").id();

    // 누른 적 없는 좋아요 해제도 멱등 — 행이 없으면 조용히 0.
    assertThat(command.setLike(fan, noteId, false).likeCount()).isZero();
    assertThat(command.setLike(fan, noteId, true).likeCount()).isEqualTo(1);
    assertThat(command.setLike(fan, noteId, true).likeCount()).isEqualTo(1);
    assertThat(query.likedNoteIds(fan, List.of(noteId))).containsExactly(noteId);

    assertThat(command.setLike(fan, noteId, false).likeCount()).isZero();
    assertThat(query.likedNoteIds(fan, List.of(noteId))).isEmpty();
  }

  @Test
  void onlyOwnerCanDelete() {
    Long author = signUp("note-e@example.com", "g-note-5");
    Long stranger = signUp("note-f@example.com", "g-note-6");
    Long noteId = command.create(author, "내 노트").id();

    assertThatThrownBy(() -> command.delete(stranger, noteId))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.NOTE_PERMISSION_DENIED));

    command.delete(author, noteId);
    assertThatThrownBy(() -> command.delete(author, noteId))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.NOTE_NOT_FOUND));
  }
}
