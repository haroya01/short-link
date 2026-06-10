package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * view limit 체크 + viewCount 증분 atomically. 반환값 &gt; 0 = 증분 됐음, 0 = limit 도달 (호출자가 expire 처리).
 * redirect 핸들러용.
 */
@Service
@RequiredArgsConstructor
public class IncrementViewCountUseCase {

  private final LinkRepository repository;

  // redirect 경로는 트랜잭션 없이 진입하는데 @Modifying UPDATE 는 활성 트랜잭션을 요구한다 —
  // 이게 빠지면 maxViews 링크의 모든 리다이렉트가 TransactionRequiredException 으로 죽는다.
  @Transactional
  public int execute(IncrementViewCountCommand command) {
    return repository.incrementViewCountIfBelowLimit(command.linkId().value());
  }
}
