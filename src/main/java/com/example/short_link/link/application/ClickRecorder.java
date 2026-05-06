package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClickRecorder {

  private final ClickEventRepository repository;

  @Transactional
  public void record(Long linkId, String referrer, String userAgent, String clientIp) {
    repository.save(new ClickEventEntity(linkId, referrer, userAgent, clientIp));
  }
}
