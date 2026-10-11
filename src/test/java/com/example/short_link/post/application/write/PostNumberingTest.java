package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.AuthorPostNumberRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostNumberingTest {

  @Mock private AuthorPostNumberRepository numbers;
  @Mock private PostRepository posts;
  @InjectMocks private PostNumbering numbering;

  private PostEntity draft(String slug) {
    return new PostEntity(7L, slug, "제목", "ko");
  }

  @ParameterizedTest
  @ValueSource(strings = {"draft-t9dwg3j", "draft-j2kile9", "draft-4f", "p-1790819294-318"})
  void aSlugTheClientMadeUpBecomesTheAuthorsNextNumber(String slug) {
    when(numbers.next(7L)).thenReturn(12L);
    PostEntity post = draft(slug);

    numbering.numberIfClientMade(post);

    assertThat(post.getSlug()).isEqualTo("12");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"my-post", "draft", "draft-notes-about-x", "draft-ABCDEFG", "p-123-45", "12"})
  void aSlugTheWriterTypedIsKept(String slug) {
    PostEntity post = draft(slug);

    numbering.numberIfClientMade(post);

    assertThat(post.getSlug()).isEqualTo(slug);
    verify(numbers, never()).next(anyLong());
  }

  @Test
  void aPostThatWasEverPublicKeepsItsAddress() {
    PostEntity post = draft("draft-sm5p3dz");
    post.publish();
    post.unpublish();

    numbering.numberIfClientMade(post);

    assertThat(post.getSlug()).isEqualTo("draft-sm5p3dz");
    verify(numbers, never()).next(anyLong());
  }

  @Test
  void aNumberTheWriterAlreadyTypedIsSkipped() {
    when(numbers.next(7L)).thenReturn(2L, 3L);
    when(posts.existsByUserIdAndSlug(7L, "2")).thenReturn(true);
    when(posts.existsByUserIdAndSlug(7L, "3")).thenReturn(false);
    PostEntity post = draft("draft-abc1234");

    numbering.numberIfClientMade(post);

    assertThat(post.getSlug()).isEqualTo("3");
    verify(numbers, times(2)).next(7L);
  }
}
