package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkRefReader {

  private final LinkRepository links;

  public Optional<LinkRef> find(LinkId linkId) {
    return links.findById(linkId.value()).map(LinkRef::of);
  }

  public Optional<LinkRef> find(ShortCode shortCode) {
    return links.findByShortCode(shortCode).map(LinkRef::of);
  }

  public List<LinkRef> findWithUrlContaining(String fragment) {
    return links.findByOriginalUrlContaining(fragment).stream().map(LinkRef::of).toList();
  }
}
