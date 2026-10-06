package com.example.short_link.note.presentation;

import com.example.short_link.note.application.read.NoteFeedView;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteThreadView;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteImages;
import com.example.short_link.note.presentation.request.CreateNoteRequest;
import com.example.short_link.note.presentation.request.EditNoteRequest;
import com.example.short_link.note.presentation.request.NoteImagePresignRequest;
import com.example.short_link.note.presentation.response.LikedIdsResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoteController {

  private final NoteQueryService query;
  private final NoteCommandService command;
  private final NoteImages images;

  @GetMapping("/api/v1/public/notes")
  public NoteFeedView everyone(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.everyone(page, size, viewerId);
  }

  @GetMapping("/api/v1/public/notes/{id}")
  public NoteThreadView thread(@AuthenticationPrincipal Long viewerId, @PathVariable Long id) {
    return query.thread(id, viewerId);
  }

  @GetMapping("/api/v1/public/profiles/{username}/notes")
  public NoteFeedView byAuthor(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.byAuthor(username, page, size, viewerId);
  }

  @GetMapping("/api/v1/notes/following")
  public NoteFeedView following(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return query.following(userId, page, size);
  }

  @PostMapping("/api/v1/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateNoteRequest request) {
    return command.create(userId, request.toDraft());
  }

  @PatchMapping("/api/v1/notes/{id}")
  public NoteView edit(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody EditNoteRequest request) {
    return command.edit(userId, id, request.body());
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

  @PostMapping("/api/v1/notes/images/presign")
  public NoteImages.PresignedImage presignImage(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody NoteImagePresignRequest request) {
    return images.presign(userId, request.contentType());
  }
}
