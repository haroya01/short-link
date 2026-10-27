package com.example.short_link.event.application.write;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.helper.EventPublicUrlBuilder;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.application.read.LinkRefReader;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventLinkIssuerTest {

  private final CreateLinkUseCase createLink = mock(CreateLinkUseCase.class);
  private final LinkRepository links = mock(LinkRepository.class);
  private final EventLinkRepository eventLinks = mock(EventLinkRepository.class);
  private final EventLinkIssuer issuer =
      new EventLinkIssuer(
          createLink,
          new LinkRefReader(links),
          eventLinks,
          new EventPublicUrlBuilder("https://kurl.me"));

  @Test
  void theIssuedLinkIsRecordedUnderTheEventWithItsId() {
    ShortCode code = new ShortCode("evt0001");
    when(createLink.execute(any())).thenReturn(new LinkCreated(code));
    LinkEntity link = withId(new LinkEntity("https://kurl.me/e/party", "evt0001", 7L, null), 42L);
    when(links.findByShortCode(code)).thenReturn(Optional.of(link));

    Optional<EventLinkIssuer.IssuedLink> issued = issuer.issue(7L, 5L, "party", "poster");

    assertThat(issued).contains(new EventLinkIssuer.IssuedLink(42L, "evt0001"));
    ArgumentCaptor<EventLinkEntity> saved = ArgumentCaptor.forClass(EventLinkEntity.class);
    verify(eventLinks).save(saved.capture());
    assertThat(saved.getValue().getLinkId()).isEqualTo(42L);
    assertThat(saved.getValue().getLabel()).isEqualTo("poster");
  }

  @Test
  void aLinkThatCannotBeReadBackIssuesNothing() {
    ShortCode code = new ShortCode("evt0002");
    when(createLink.execute(any())).thenReturn(new LinkCreated(code));
    when(links.findByShortCode(code)).thenReturn(Optional.empty());

    assertThat(issuer.issue(7L, 5L, "party", "poster")).isEmpty();
    verify(eventLinks, never()).save(any());
  }
}
