package com.example.short_link.user.application.moderation;

import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class UserBlockCheckerAdapter implements UserBlockChecker {

  private final BlockRepository blockRepository;
  private final MuteRepository muteRepository;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public boolean isBlocked(Long blockerId, Long blockedId) {
    if (blockerId == null || blockedId == null) {
      return false;
    }
    return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
  }

  @Override
  @Transactional(readOnly = true)
  public BlockRelation between(Long viewerId, Long otherId) {
    if (viewerId == null || otherId == null || viewerId.equals(otherId)) {
      return BlockRelation.NONE;
    }
    boolean blockedByViewer = false;
    boolean blocksViewer = false;
    for (UserBlockEntity block : blockRepository.findBetween(viewerId, otherId)) {
      if (block.getBlockerId().equals(viewerId)) {
        blockedByViewer = true;
      } else {
        blocksViewer = true;
      }
    }
    return new BlockRelation(blockedByViewer, blocksViewer);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean silences(Long recipientId, Long actorId) {
    if (recipientId == null || actorId == null) {
      return false;
    }
    return muteRepository.silences(recipientId, actorId, clock.instant());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean silences(Long recipientId, Long actorId, Long remoteActorId, Long conversationId) {
    if (recipientId == null
        || (actorId == null && remoteActorId == null && conversationId == null)) {
      return false;
    }
    return muteRepository.silences(
        recipientId, actorId, remoteActorId, conversationId, clock.instant());
  }
}
