package com.example.short_link.link;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {

  private static final int MAX_ATTEMPTS = 5;

  private final LinkRepository repository;
  private final ShortCodeGenerator generator;
  private final String baseUrl;

  public LinkService(
      LinkRepository repository,
      ShortCodeGenerator generator,
      @Value("${short-link.base-url}") String baseUrl) {
    this.repository = repository;
    this.generator = generator;
    this.baseUrl = baseUrl;
  }

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
