package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.read.BookmarkFolderView;
import com.example.short_link.post.domain.BookmarkFolderEntity;
import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.repository.BookmarkFolderRepository;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.exception.PostException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookmarkFolderUseCaseTest {

  @Mock private BookmarkFolderRepository folderRepository;
  @Mock private PostBookmarkRepository bookmarkRepository;

  private BookmarkFolderUseCase useCase() {
    return new BookmarkFolderUseCase(folderRepository, bookmarkRepository);
  }

  private BookmarkFolderEntity folder(long id, long userId, String name) {
    BookmarkFolderEntity f = new BookmarkFolderEntity(userId, name);
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  @Test
  void createTrimsAndReturnsZeroCount() {
    when(folderRepository.existsByUserIdAndName(1L, "읽을거리")).thenReturn(false);
    when(folderRepository.save(any())).thenAnswer(inv -> folder(10L, 1L, "읽을거리"));

    BookmarkFolderView view = useCase().create(1L, "  읽을거리  ");

    assertThat(view.id()).isEqualTo(10L);
    assertThat(view.name()).isEqualTo("읽을거리");
    assertThat(view.count()).isZero();
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> useCase().create(1L, "   ")).isInstanceOf(PostException.class);
    verify(folderRepository, never()).save(any());
  }

  @Test
  void createRejectsDuplicateName() {
    when(folderRepository.existsByUserIdAndName(1L, "중복")).thenReturn(true);
    assertThatThrownBy(() -> useCase().create(1L, "중복")).isInstanceOf(PostException.class);
    verify(folderRepository, never()).save(any());
  }

  @Test
  void renameUpdatesName() {
    when(folderRepository.findByIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(folder(10L, 1L, "옛이름")));
    when(folderRepository.existsByUserIdAndName(1L, "새이름")).thenReturn(false);

    BookmarkFolderView view = useCase().rename(1L, 10L, "새이름");

    assertThat(view.name()).isEqualTo("새이름");
    verify(folderRepository).save(any());
  }

  @Test
  void renameToOwnNameSkipsConflictCheck() {
    when(folderRepository.findByIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(folder(10L, 1L, "같은이름")));

    BookmarkFolderView view = useCase().rename(1L, 10L, "같은이름");

    assertThat(view.name()).isEqualTo("같은이름");
    verify(folderRepository, never()).existsByUserIdAndName(any(), any());
  }

  @Test
  void renameMissingFolderThrows() {
    when(folderRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase().rename(1L, 99L, "x")).isInstanceOf(PostException.class);
  }

  @Test
  void deleteRemovesOwnedFolder() {
    BookmarkFolderEntity f = folder(10L, 1L, "f");
    when(folderRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(f));

    useCase().delete(1L, 10L);

    verify(folderRepository).delete(f);
  }

  @Test
  void deleteMissingFolderThrows() {
    when(folderRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase().delete(1L, 99L)).isInstanceOf(PostException.class);
  }

  @Test
  void moveBookmarkFilesIntoFolder() {
    when(folderRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(folder(10L, 1L, "f")));
    PostBookmarkEntity bm = new PostBookmarkEntity(42L, 1L);
    when(bookmarkRepository.findByPostIdAndUserId(42L, 1L)).thenReturn(Optional.of(bm));

    useCase().moveBookmark(1L, 42L, 10L);

    assertThat(bm.getFolderId()).isEqualTo(10L);
    verify(bookmarkRepository).save(bm);
  }

  @Test
  void moveBookmarkUnfilesWhenFolderNull() {
    PostBookmarkEntity bm = new PostBookmarkEntity(42L, 1L);
    bm.moveToFolder(10L);
    when(bookmarkRepository.findByPostIdAndUserId(42L, 1L)).thenReturn(Optional.of(bm));

    useCase().moveBookmark(1L, 42L, null);

    assertThat(bm.getFolderId()).isNull();
    verify(folderRepository, never()).findByIdAndUserId(any(), any());
  }

  @Test
  void moveBookmarkRejectsForeignFolder() {
    when(folderRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase().moveBookmark(1L, 42L, 10L))
        .isInstanceOf(PostException.class);
    verify(bookmarkRepository, never()).save(any());
  }

  @Test
  void moveBookmarkRejectsMissingBookmark() {
    when(folderRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(folder(10L, 1L, "f")));
    when(bookmarkRepository.findByPostIdAndUserId(42L, 1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase().moveBookmark(1L, 42L, 10L))
        .isInstanceOf(PostException.class);
  }
}
