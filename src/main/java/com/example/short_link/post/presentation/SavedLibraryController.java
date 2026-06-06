package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.BookmarkFolderView;
import com.example.short_link.post.application.read.SavedLibraryQueryService;
import com.example.short_link.post.application.read.SavedView;
import com.example.short_link.post.application.write.BookmarkFolderUseCase;
import com.example.short_link.post.presentation.request.BookmarkFolderNameRequest;
import com.example.short_link.post.presentation.request.MoveBookmarkRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's "보관함" (스마트 셸프): the caller's bookmarks as full feed cards, plus the folders they
 * organize them into. Every endpoint is scoped to the authenticated caller.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SavedLibraryController {

  private final SavedLibraryQueryService savedLibraryQueryService;
  private final BookmarkFolderUseCase bookmarkFolderUseCase;

  @GetMapping("/me/saved")
  public List<SavedView> saved(@AuthenticationPrincipal Long userId) {
    return savedLibraryQueryService.saved(userId);
  }

  @PutMapping("/me/saved/{postId}/folder")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void moveToFolder(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestBody MoveBookmarkRequest req) {
    bookmarkFolderUseCase.moveBookmark(userId, postId, req.folderId());
  }

  @GetMapping("/bookmarks/folders")
  public List<BookmarkFolderView> folders(@AuthenticationPrincipal Long userId) {
    return savedLibraryQueryService.folders(userId);
  }

  @PostMapping("/bookmarks/folders")
  @ResponseStatus(HttpStatus.CREATED)
  public BookmarkFolderView createFolder(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BookmarkFolderNameRequest req) {
    return bookmarkFolderUseCase.create(userId, req.name());
  }

  @PatchMapping("/bookmarks/folders/{id}")
  public BookmarkFolderView renameFolder(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody BookmarkFolderNameRequest req) {
    return bookmarkFolderUseCase.rename(userId, id, req.name());
  }

  @DeleteMapping("/bookmarks/folders/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteFolder(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    bookmarkFolderUseCase.delete(userId, id);
  }
}
