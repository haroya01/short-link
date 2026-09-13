package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 한도 검사와 증분은 원자적이다. 반환값이 0이면 한도 도달로 호출자가 만료 처리하고, 양수이면 증분에 성공한 것이다. */
@Service
@RequiredArgsConstructor
public class IncrementViewCountUseCase {

  private final LinkRepository repository;

  // 트랜잭션 없이 진입하는 redirect 경로에서도 @Modifying UPDATE를 실행할 트랜잭션이 필요하다.
  @Transactional
  public int execute(IncrementViewCountCommand command) {
    return repository.incrementViewCountIfBelowLimit(command.linkId().value());
  }
}
