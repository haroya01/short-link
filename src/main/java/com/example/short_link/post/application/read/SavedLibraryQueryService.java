package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.FolderBookmarkCount;
import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.BookmarkFolderRepository;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The owner's "보관함" (스마트 셸프): their bookmarks as full feed cards plus the folders they're filed
 * under. Stale bookmarks (post deleted/unpublished or author gone) are skipped, mirroring the liked
 * list. Posts and authors are batch-loaded to avoid an N+1 over the list.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavedLibraryQueryService {

  private final PostBookmarkRepository postBookmarkRepository;
  private final BookmarkFolderRepository bookmarkFolderRepository;
  private final PostRepository postRepository;
  private final PostFeedItemAssembler feedItemAssembler;

  public List<SavedView> saved(Long userId) {
    List<PostBookmarkEntity> bookmarks =
        postBookmarkRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (bookmarks.isEmpty()) return List.of();

    Map<Long, Long> folderByPost = new HashMap<>();
    for (PostBookmarkEntity b : bookmarks) {
      folderByPost.put(b.getPostId(), b.getFolderId());
    }

    List<Long> postIds = bookmarks.stream().map(PostBookmarkEntity::getPostId).toList();
    Map<Long, PostEntity> published =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(PostEntity::isPublished)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));

    List<PostEntity> ordered =
        bookmarks.stream().map(b -> published.get(b.getPostId())).filter(Objects::nonNull).toList();
    return feedItemAssembler.assemble(ordered).stream()
        .map(item -> SavedView.of(item, folderByPost.get(item.id())))
        .toList();
  }

  public List<BookmarkFolderView> folders(Long userId) {
    Map<Long, Long> counts =
        postBookmarkRepository.countByFolder(userId).stream()
            .collect(Collectors.toMap(FolderBookmarkCount::folderId, FolderBookmarkCount::count));
    return bookmarkFolderRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
        .map(
            f -> new BookmarkFolderView(f.getId(), f.getName(), counts.getOrDefault(f.getId(), 0L)))
        .toList();
  }
}
