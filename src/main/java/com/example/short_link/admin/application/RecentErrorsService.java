package com.example.short_link.admin.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecentErrorsService {

  private static final int DEFAULT_LIMIT = 50;

  private final RecentErrorsBuffer buffer;

  public List<RecentError> recent(Integer limit) {
    int n = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
    return buffer.snapshot(n);
  }
}
