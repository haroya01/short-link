package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * view limit 체크 + viewCount 증분 atomically. 반환값 &gt; 0 = 증분 됐음, 0 = limit 도달 (호출자가 expire 처리).
 * redirect 핸들러용.
 */
@Service
@RequiredArgsConstructor
public class IncrementViewCountUseCase {

  private final LinkRepository repository;

  public int execute(IncrementViewCountCommand command) {
    return repository.incrementViewCountIfBelowLimit(command.linkId());
  }
}
