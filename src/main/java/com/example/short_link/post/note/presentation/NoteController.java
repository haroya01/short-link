package com.example.short_link.post.note.presentation;

import com.example.short_link.post.note.application.read.NoteFeedView;
import com.example.short_link.post.note.application.read.NoteQueryService;
import com.example.short_link.post.note.application.write.NoteCommandService;
import com.example.short_link.post.note.domain.NoteRow;
import com.example.short_link.post.note.presentation.request.CreateNoteRequest;
import com.example.short_link.post.note.presentation.response.LikedIdsResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 짧은 글(노트). 읽기는 공개(/public), 쓰기·좋아요는 인증. likedByMe 는 피드에 싣지 않고 배치 상태 조회로 분리한다 — 공개 피드 캐시가 사용자별로
 * 갈라지지 않게(#538 과 동일).
 */
@RestController
@RequiredArgsConstructor
public class NoteController {

  private final NoteQueryService query;
  private final NoteCommandService command;

  @GetMapping("/api/v1/public/notes")
  public NoteFeedView feed(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return query.feed(page, size);
  }

  @PostMapping("/api/v1/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteRow create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateNoteRequest request) {
    return command.create(userId, request.body());
  }

  @DeleteMapping("/api/v1/notes/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    command.delete(userId, id);
  }

  @PutMapping("/api/v1/notes/{id}/like")
  public NoteCommandService.LikeStatus like(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setLike(userId, id, true);
  }

  @DeleteMapping("/api/v1/notes/{id}/like")
  public NoteCommandService.LikeStatus unlike(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return command.setLike(userId, id, false);
  }

  @GetMapping("/api/v1/notes/like-status")
  public LikedIdsResponse likeStatus(
      @AuthenticationPrincipal Long userId, @RequestParam List<Long> ids) {
    return new LikedIdsResponse(query.likedNoteIds(userId, ids));
  }
}
