package com.example.short_link.blogimport.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.domain.BlogAddress;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.post.application.read.ImportedPostQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ImportSourceQueryServiceTest {

  private final ImportSourceRepository sources = mock(ImportSourceRepository.class);
  private final ImportedPostQueryService posts = mock(ImportedPostQueryService.class);
  private final ImportSourceQueryService service = new ImportSourceQueryService(sources, posts);

  private static ImportSourceEntity source(long id, Long owner, String url) {
    ImportSourceEntity source =
        new ImportSourceEntity(owner, BlogAddress.parse(url).orElseThrow(), "t", false, "kurl-A");
    ReflectionTestUtils.setField(source, "id", id);
    return source;
  }

  @Test
  void theListCountsEverySourcesPostsInOneRead() {
    ImportSourceEntity naver = source(1L, 7L, "blog.naver.com/a");
    ImportSourceEntity velog = source(2L, 7L, "velog.io/@a");
    naver.verify(Instant.parse("2026-10-15T00:00:00Z"), 3);
    when(sources.findAllByUserId(7L)).thenReturn(List.of(velog, naver));
    when(posts.publishedCounts(List.of(2L, 1L))).thenReturn(Map.of(1L, 3L));

    List<ImportSourceView> views = service.list(7L);

    assertThat(views).extracting(ImportSourceView::platform).containsExactly("VELOG", "NAVER");
    assertThat(views).extracting(ImportSourceView::importedCount).containsExactly(0L, 3L);
    assertThat(views.get(1).progress()).isEqualTo(new ImportSourceView.Progress(3, 0, 0));
    assertThat(views.get(0).progress()).isNull();
  }

  @Test
  void anEmptyListReadsNoCounts() {
    when(sources.findAllByUserId(7L)).thenReturn(List.of());

    assertThat(service.list(7L)).isEmpty();
    verify(posts, never()).publishedCounts(any());
  }

  @Test
  void aSourceIsOnlyShownToItsOwner() {
    when(sources.findById(1L)).thenReturn(Optional.of(source(1L, 7L, "velog.io/@a")));
    when(posts.publishedCounts(List.of(1L))).thenReturn(Map.of(1L, 2L));

    assertThat(service.get(7L, 1L).importedCount()).isEqualTo(2L);
    assertThatThrownBy(() -> service.get(8L, 1L)).isInstanceOf(BlogImportException.class);
    assertThatThrownBy(() -> service.get(7L, 2L)).isInstanceOf(BlogImportException.class);
  }
}
