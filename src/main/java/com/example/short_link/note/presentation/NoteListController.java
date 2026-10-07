package com.example.short_link.note.presentation;

import com.example.short_link.note.application.read.NoteFeedView;
import com.example.short_link.note.application.write.NoteListService;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.presentation.request.NoteListRequest;
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
public class NoteListController {

  private final NoteListService lists;

  @GetMapping("/api/v1/notes/lists")
  public List<NoteListService.ListView> mine(@AuthenticationPrincipal Long userId) {
    return lists.mine(userId);
  }

  @PostMapping("/api/v1/notes/lists")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteListService.ListView create(
      @AuthenticationPrincipal Long userId, @RequestBody NoteListRequest request) {
    return lists.create(userId, request.title());
  }

  @PatchMapping("/api/v1/notes/lists/{id}")
  public NoteListService.ListView rename(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody NoteListRequest request) {
    return lists.rename(userId, id, request.title());
  }

  @DeleteMapping("/api/v1/notes/lists/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    lists.delete(userId, id);
  }

  @GetMapping("/api/v1/notes/lists/{id}/members")
  public List<NoteAuthor> members(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return lists.members(userId, id);
  }

  @PutMapping("/api/v1/notes/lists/{id}/members/{username}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void add(
      @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable String username) {
    lists.add(userId, id, username);
  }

  @DeleteMapping("/api/v1/notes/lists/{id}/members/{username}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(
      @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable String username) {
    lists.remove(userId, id, username);
  }

  @GetMapping("/api/v1/notes/lists/{id}/notes")
  public NoteFeedView feed(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return lists.feed(userId, id, page, size);
  }

  @GetMapping("/api/v1/notes/list-memberships/{username}")
  public NoteListService.Membership membership(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    return lists.membership(userId, username);
  }
}
