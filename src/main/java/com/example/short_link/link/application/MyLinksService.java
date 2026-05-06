package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyLinksService {

  private final LinkRepository repository;

  @Transactional(readOnly = true)
  public List<MyLink> myLinks(Long userId) {
    return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(
            link ->
                new MyLink(
                    link.getShortCode(),
                    link.getOriginalUrl(),
                    link.getCreatedAt(),
                    link.getExpiresAt()))
        .toList();
  }
}
