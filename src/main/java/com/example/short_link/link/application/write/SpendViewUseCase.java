package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpendViewUseCase {

  private final LinkRepository repository;

  // 트랜잭션 없이 진입하는 redirect 경로에서도 @Modifying UPDATE를 실행할 트랜잭션이 필요하다.
  @Transactional
  public boolean spend(LinkId linkId) {
    return repository.spendView(linkId);
  }
}
