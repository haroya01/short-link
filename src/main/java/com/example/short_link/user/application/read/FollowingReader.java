package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.repository.FollowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FollowingReader {

  private final FollowRepository follows;

  public List<Long> followingIds(Long followerId) {
    return follows.findFollowingIds(followerId);
  }

  public boolean follows(Long followerId, Long followingId) {
    return follows.existsByFollowerIdAndFollowingId(followerId, followingId);
  }
}
