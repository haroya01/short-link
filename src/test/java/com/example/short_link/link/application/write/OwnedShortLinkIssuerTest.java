package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.link.ShortLinkIssuer;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OwnedShortLinkIssuerTest {

  private final CreateLinkUseCase createLink = mock(CreateLinkUseCase.class);
  private final LinkRepository links = mock(LinkRepository.class);
  private final OwnedShortLinkIssuer issuer =
      new OwnedShortLinkIssuer(createLink, links, new ShortLinkUrlBuilder("https://kurl.me"));

  @Test
  void issuesAnOwnedReusableLinkThroughTheOrdinaryPath() {
    when(createLink.execute(any())).thenReturn(new LinkCreated(ShortCode.of("abc123")));
    LinkEntity link = new LinkEntity("https://velog.io/@a/1", "abc123", 7L, null);
    ReflectionTestUtils.setField(link, "id", 55L);
    when(links.findByShortCode(ShortCode.of("abc123"))).thenReturn(Optional.of(link));

    Optional<ShortLinkIssuer.Issued> issued = issuer.issue(7L, "https://velog.io/@a/1");

    assertThat(issued)
        .contains(new ShortLinkIssuer.Issued(55L, "abc123", "https://kurl.me/abc123"));
    ArgumentCaptor<CreateLinkCommand> command = ArgumentCaptor.forClass(CreateLinkCommand.class);
    org.mockito.Mockito.verify(createLink).execute(command.capture());
    assertThat(command.getValue().userId()).isEqualTo(7L);
    assertThat(command.getValue().deduplicate()).isTrue();
    assertThat(command.getValue().customCode()).isNull();
  }

  @Test
  void anAddressThatCannotBeShortenedIssuesNothing() {
    when(createLink.execute(any()))
        .thenThrow(new LinkException(LinkErrorCode.LINK_QUOTA_EXCEEDED, 200));

    assertThat(issuer.issue(7L, "https://velog.io/@a/1")).isEmpty();
  }
}
