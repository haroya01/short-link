package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkDetailView;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.tag.application.LinkTagService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkDetailServiceTest {

  @Mock private LinkRepository repository;
  @Mock private LinkTagService linkTagService;
  @Mock private LinkAccessGuard accessGuard;

  private LinkDetailQueryService service;

  @BeforeEach
  void setUp() {
    service = new LinkDetailQueryService(repository, linkTagService, accessGuard);
  }

  @Test
  void detailReturnsResponseForViewer() {
    LinkEntity link = new LinkEntity("https://example.com", "abc", 7L, null);
    link.setMaxViews(10);
    link.incrementViewCount();
    link.changeStatsVisibility(true);
    link.updateNote("note");
    link.updateExpiredMessage("expired");
    when(repository.findByShortCode("abc")).thenReturn(Optional.of(link));
    when(linkTagService.tagNamesFor(7L, "abc")).thenReturn(List.of("a", "b"));

    LinkDetailView r = service.detail(7L, "abc");
    assertThat(r.shortCode()).isEqualTo("abc");
    assertThat(r.originalUrl()).isEqualTo("https://example.com");
    assertThat(r.maxViews()).isEqualTo(10);
    assertThat(r.viewCount()).isEqualTo(1);
    assertThat(r.statsPublic()).isTrue();
    assertThat(r.tags()).containsExactly("a", "b");
    assertThat(r.note()).isEqualTo("note");
    assertThat(r.expiredMessage()).isEqualTo("expired");
  }

  @Test
  void detailThrowsWhenLinkMissing() {
    when(repository.findByShortCode("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.detail(7L, "missing")).isInstanceOf(LinkException.class);
  }

  @Test
  void detailRespectsAccessGuard() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    when(repository.findByShortCode("abc")).thenReturn(Optional.of(link));
    doThrow(new LinkException(LinkErrorCode.LINK_NOT_OWNED, "abc"))
        .when(accessGuard)
        .requireView(any(), any());
    assertThatThrownBy(() -> service.detail(99L, "abc")).isInstanceOf(LinkException.class);
  }
}
