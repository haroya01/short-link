package com.example.short_link.link;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkService {

  private static final int MAX_ATTEMPTS = 5;

  private final LinkRepository repository;
  private final ShortCodeGenerator generator;

  @Value("${short-link.base-url}")
  private String baseUrl;

  @Transactional
  public CreateLinkResponse create(String url) {
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String code = generator.generate();
      if (!repository.existsByShortCode(code)) {
        LinkEntity saved = repository.save(new LinkEntity(url, code));
        return new CreateLinkResponse(saved.getShortCode(), baseUrl + "/" + saved.getShortCode());
      }
    }
    throw new ShortCodeGenerationException();
  }
}
