package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LinkService {

  private static final int MAX_ATTEMPTS = 5;

  private final LinkRepository repository;
  private final ShortCodeGenerator generator;

  public LinkCreated create(String url) {
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String code = generator.generate();
      try {
        LinkEntity saved = repository.save(new LinkEntity(url, code));
        return new LinkCreated(saved.getShortCode());
      } catch (DataIntegrityViolationException ignored) {
      }
    }
    throw new ShortCodeGenerationException();
  }
}
