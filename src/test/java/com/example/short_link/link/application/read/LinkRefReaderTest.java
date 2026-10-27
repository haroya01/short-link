package com.example.short_link.link.application.read;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkRefReaderTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkRefReader reader = new LinkRefReader(links);
  private final LinkEntity link =
      withId(new LinkEntity("https://example.com/a", "ref0001", 7L, null), 3L);
  private final LinkRef ref =
      new LinkRef(new LinkId(3L), 7L, new ShortCode("ref0001"), "https://example.com/a");

  @Test
  void aLinkReadsAsItsIdOwnerCodeAndDestination() {
    when(links.findById(3L)).thenReturn(Optional.of(link));
    when(links.findByShortCode(new ShortCode("ref0001"))).thenReturn(Optional.of(link));
    when(links.findByOriginalUrlContaining("example.com")).thenReturn(List.of(link));

    assertThat(reader.find(new LinkId(3L))).contains(ref);
    assertThat(reader.find(new ShortCode("ref0001"))).contains(ref);
    assertThat(reader.findWithUrlContaining("example.com")).containsExactly(ref);
  }

  @Test
  void aMissingLinkReadsAsEmpty() {
    when(links.findById(9L)).thenReturn(Optional.empty());

    assertThat(reader.find(new LinkId(9L))).isEmpty();
  }
}
