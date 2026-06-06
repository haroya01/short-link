package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.BookmarkFolderView;
import com.example.short_link.post.domain.BookmarkFolderEntity;
import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.repository.BookmarkFolderRepository;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create / rename / delete bookmark folders and file bookmarks into them. Every operation is scoped
 * to the caller: a folder is only ever found via (id, userId), so one user can't touch another's
 * shelf. Deleting a folder unfiles its bookmarks (the FK is ON DELETE SET NULL) rather than
 * dropping them from the reading list.
 */
@Service
@RequiredArgsConstructor
public class BookmarkFolderUseCase {

  private static final int MAX_NAME_LENGTH = 60;

  private final BookmarkFolderRepository bookmarkFolderRepository;
  private final PostBookmarkRepository postBookmarkRepository;

  @Transactional
  public BookmarkFolderView create(Long userId, String name) {
    String clean = normalize(name);
    if (bookmarkFolderRepository.existsByUserIdAndName(userId, clean)) {
      throw new PostException(PostErrorCode.BOOKMARK_FOLDER_NAME_CONFLICT, clean);
    }
    BookmarkFolderEntity saved =
        bookmarkFolderRepository.save(new BookmarkFolderEntity(userId, clean));
    return new BookmarkFolderView(saved.getId(), saved.getName(), 0);
  }

  @Transactional
  public BookmarkFolderView rename(Long userId, Long folderId, String name) {
    String clean = normalize(name);
    BookmarkFolderEntity folder = require(userId, folderId);
    if (!folder.getName().equals(clean)
        && bookmarkFolderRepository.existsByUserIdAndName(userId, clean)) {
      throw new PostException(PostErrorCode.BOOKMARK_FOLDER_NAME_CONFLICT, clean);
    }
    folder.rename(clean);
    bookmarkFolderRepository.save(folder);
    return new BookmarkFolderView(folder.getId(), folder.getName(), 0);
  }

  @Transactional
  public void delete(Long userId, Long folderId) {
    bookmarkFolderRepository.delete(require(userId, folderId));
  }

  /** File the caller's bookmark on {@code postId} under {@code folderId} (null = unfile). */
  @Transactional
  public void moveBookmark(Long userId, Long postId, Long folderId) {
    if (folderId != null) {
      require(userId, folderId);
    }
    PostBookmarkEntity bookmark =
        postBookmarkRepository
            .findByPostIdAndUserId(postId, userId)
            .orElseThrow(() -> new PostException(PostErrorCode.BOOKMARK_NOT_FOUND, postId));
    bookmark.moveToFolder(folderId);
    postBookmarkRepository.save(bookmark);
  }

  private BookmarkFolderEntity require(Long userId, Long folderId) {
    return bookmarkFolderRepository
        .findByIdAndUserId(folderId, userId)
        .orElseThrow(() -> new PostException(PostErrorCode.BOOKMARK_FOLDER_NOT_FOUND, folderId));
  }

  private String normalize(String name) {
    String clean = name == null ? "" : name.strip();
    if (clean.isEmpty()) {
      throw new PostException(PostErrorCode.BOOKMARK_FOLDER_NAME_REQUIRED);
    }
    return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
  }
}
