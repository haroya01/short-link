package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {

  private static final int MAX_ATTEMPTS = 5;

  private final LinkRepository repository;
  private final ShortCodeGenerator generator;

  public LinkService(LinkRepository repository, ShortCodeGenerator generator) {
    this.repository = repository;
    this.generator = generator;
  }

  @Transactional
  public LinkEntity create(String url) {
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String code = generator.generate();
      if (!repository.existsByShortCode(code)) {
        return repository.save(new LinkEntity(url, code));
      }
    }
    throw new ShortCodeGenerationException();
  }
}
