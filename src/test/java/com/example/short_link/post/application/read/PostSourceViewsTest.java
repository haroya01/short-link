package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.ImportedPostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostSourceViewsTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
  private final PostSourceViews views =
      new PostSourceViews(new ShortLinkUrlBuilder("https://kurl.me"));

  private static PostEntity imported(Long linkId, String code, boolean excerptOnly) {
    PostEntity post =
        PostEntity.importedDraft(7L, "draft-a", 3L, "NAVER", "https://blog.naver.com/a/1");
    post.updateTitle("t");
    post.publishImported(excerptOnly, linkId, code, false, NOW, NOW);
    return post;
  }

  @Test
  void anImportedPostNamesItsOriginAndShortLink() {
    PostSourceView source = views.of(imported(55L, "abc123", true));

    assertThat(source)
        .isEqualTo(
            new PostSourceView(
                "NAVER", "https://blog.naver.com/a/1", "https://kurl.me/abc123", true));
  }

  @Test
  void aShortLinkThatIsGoneIsNotShown() {
    assertThat(views.of(imported(null, "abc123", false)).shortUrl()).isNull();

    PostEntity unlinked = imported(55L, "abc123", false);
    org.springframework.test.util.ReflectionTestUtils.setField(unlinked, "sourceLinkId", null);
    assertThat(views.of(unlinked).shortUrl()).isNull();
  }

  @Test
  void aWrittenPostHasNoSource() {
    assertThat(views.of(new PostEntity(7L, "mine", "t", "ko"))).isNull();
  }

  @Test
  void importedCountsComeFromOneRead() {
    ImportedPostRepository repository = mock(ImportedPostRepository.class);
    when(repository.countPublishedByImportSourceIds(List.of(1L))).thenReturn(Map.of(1L, 4L));

    assertThat(new ImportedPostQueryService(repository).publishedCounts(List.of(1L)))
        .containsEntry(1L, 4L);
  }
}
