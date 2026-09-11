package com.example.short_link.admin.application;

import com.example.short_link.admin.application.dto.RecentError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Retains a bounded history and presents the newest errors first. */
@Component
public class RecentErrorsBuffer {

  private final Deque<RecentError> buffer = new ConcurrentLinkedDeque<>();
  private final int capacity;

  public RecentErrorsBuffer(@Value("${short-link.admin.recent-errors-capacity:200}") int capacity) {
    this.capacity = Math.max(10, capacity);
  }

  public void record(RecentError error) {
    buffer.addLast(error);
    while (buffer.size() > capacity) {
      buffer.pollFirst();
    }
  }

  public List<RecentError> snapshot(int limit) {
    int size = Math.min(Math.max(1, limit), capacity);
    List<RecentError> list = new ArrayList<>(buffer);
    Collections.reverse(list);
    return list.size() <= size ? list : list.subList(0, size);
  }
}
