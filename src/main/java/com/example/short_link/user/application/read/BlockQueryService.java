package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The viewer's "blocked users" list — drives the manage/unblock screen and the client-side content
 * filter (the app hides blocked authors' posts/comments locally from this set). Newest block first;
 * users that no longer have a handle are skipped while keeping the order.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockQueryService {

  private final UserRepository userRepository;
  private final BlockRepository blockRepository;

  public List<BlockedUserView> myBlocks(Long blockerId) {
    List<Long> ids = blockRepository.findBlockedIds(blockerId);
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, UserEntity> byId =
        userRepository.findAllByIdIn(ids).stream()
            .filter(u -> u.getUsername() != null)
            .collect(Collectors.toMap(UserEntity::getId, u -> u));
    return ids.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(u -> new BlockedUserView(u.getId(), u.getUsername(), u.getAvatarUrl()))
        .toList();
  }
}
