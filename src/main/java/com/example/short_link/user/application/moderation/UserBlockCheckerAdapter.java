package com.example.short_link.user.application.moderation;

import com.example.short_link.common.user.BlockEdge;
import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.SilenceCheck;
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
  public boolean isBlocked(BlockEdge edge) {
    if (edge.blocker() == null || edge.blocked() == null) {
      return false;
    }
    return blockRepository.exists(edge);
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
  public boolean silences(SilenceCheck check) {
    if (check.recipientId() == null || (check.actor().isNone() && check.conversationId() == null)) {
      return false;
    }
    return muteRepository.silences(check, clock.instant());
  }
}
