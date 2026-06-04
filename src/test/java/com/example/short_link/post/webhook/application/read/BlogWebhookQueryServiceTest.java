package com.example.short_link.post.webhook.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.webhook.application.dto.BlogWebhookSummary;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlogWebhookQueryServiceTest {

  @Mock private BlogWebhookRepository repository;

  @Test
  void mapsHooksToSummariesWithoutSecret() {
    BlogWebhookEntity hook =
        new BlogWebhookEntity(
            7L,
            "https://example.com/h",
            "topsecret",
            "mine",
            BlogWebhookFormat.GENERIC,
            EnumSet.of(BlogInteractionType.LIKE, BlogInteractionType.COMMENT));
    when(repository.findAllByUserId(7L)).thenReturn(List.of(hook));

    List<BlogWebhookSummary> list = new BlogWebhookQueryService(repository).listForUser(7L);

    assertThat(list).hasSize(1);
    BlogWebhookSummary s = list.get(0);
    assertThat(s.url()).isEqualTo("https://example.com/h");
    assertThat(s.name()).isEqualTo("mine");
    assertThat(s.enabled()).isTrue();
    assertThat(s.events())
        .containsExactlyInAnyOrder(BlogInteractionType.LIKE, BlogInteractionType.COMMENT);
  }
}
