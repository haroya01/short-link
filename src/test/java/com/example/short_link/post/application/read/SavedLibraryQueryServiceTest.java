package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.BookmarkFolderEntity;
import com.example.short_link.post.domain.FolderBookmarkCount;
import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.BookmarkFolderRepository;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SavedLibraryQueryServiceTest {

  @Mock private PostBookmarkRepository bookmarkRepository;
  @Mock private BookmarkFolderRepository folderRepository;
  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;

  private SavedLibraryQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new SavedLibraryQueryService(
            bookmarkRepository,
            folderRepository,
            postRepository,
            new PostFeedItemAssembler(userRepository));
  }

  private PostEntity publishedPost(long id, long authorId, String slug) {
    PostEntity p = new PostEntity(authorId, slug, "Title " + slug, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private UserEntity author(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private PostBookmarkEntity bookmark(long postId, Long folderId) {
    PostBookmarkEntity b = new PostBookmarkEntity(postId, 9L);
    if (folderId != null) b.moveToFolder(folderId);
    return b;
  }

  @Test
  void savedKeepsBookmarkOrderAttachesFolderAndSkipsStale() {
    when(bookmarkRepository.findAllByUserIdOrderByCreatedAtDesc(9L))
        .thenReturn(List.of(bookmark(1L, 100L), bookmark(2L, null), bookmark(3L, null)));
    PostEntity p1 = publishedPost(1L, 500L, "a");
    PostEntity p3 = publishedPost(3L, 500L, "c");
    PostEntity p2 = new PostEntity(500L, "b", "B", "ko"); // DRAFT → filtered out
    ReflectionTestUtils.setField(p2, "id", 2L);
    when(postRepository.findAllByIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(p1, p2, p3));
    when(userRepository.findAllByIdIn(List.of(500L))).thenReturn(List.of(author(500L, "alice")));

    List<SavedView> saved = service.saved(9L);

    assertThat(saved)
        .extracting(SavedView::id, SavedView::folderId)
        .containsExactly(tuple(1L, 100L), tuple(3L, null));
    assertThat(saved.get(0).author().username()).isEqualTo("alice");
  }

  @Test
  void savedEmptyWhenNoBookmarks() {
    when(bookmarkRepository.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
    assertThat(service.saved(9L)).isEmpty();
  }

  @Test
  void foldersCarryCountsDefaultingToZero() {
    BookmarkFolderEntity f1 = new BookmarkFolderEntity(9L, "나중에");
    BookmarkFolderEntity f2 = new BookmarkFolderEntity(9L, "영감");
    ReflectionTestUtils.setField(f1, "id", 100L);
    ReflectionTestUtils.setField(f2, "id", 200L);
    when(bookmarkRepository.countByFolder(9L))
        .thenReturn(List.of(new FolderBookmarkCount(100L, 3L)));
    when(folderRepository.findAllByUserIdOrderByCreatedAtAsc(9L)).thenReturn(List.of(f1, f2));

    List<BookmarkFolderView> folders = service.folders(9L);

    assertThat(folders)
        .extracting(BookmarkFolderView::id, BookmarkFolderView::name, BookmarkFolderView::count)
        .containsExactly(tuple(100L, "나중에", 3L), tuple(200L, "영감", 0L));
  }
}
