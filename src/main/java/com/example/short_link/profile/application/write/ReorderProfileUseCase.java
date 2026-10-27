package com.example.short_link.profile.application.write;

import com.example.short_link.link.application.write.LinkProfilePlacement;
import com.example.short_link.link.application.write.ProfileLinkSlot;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReorderProfileUseCase {

  private final LinkProfilePlacement linkPlacement;
  private final ProfileBlockRepository profileBlockRepository;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public void execute(ReorderProfileCommand cmd) {
    Map<String, ProfileLinkSlot> ownedLinks = linkPlacement.placedSlots(cmd.userId());
    Map<Long, ProfileBlockEntity> ownedBlocks = new HashMap<>();
    for (var block : profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(cmd.userId())) {
      ownedBlocks.put(block.getId(), block);
    }
    int order = 1;
    for (ReorderItem item : cmd.items()) {
      if (item == null || item.kind() == null || item.id() == null) continue;
      switch (item.kind().toUpperCase(Locale.ROOT)) {
        case "LINK" -> {
          ProfileLinkSlot link = ownedLinks.get(item.id());
          if (link != null) link.placeAt(order++);
        }
        case "BLOCK" -> {
          ProfileBlockEntity block = parseBlockId(item.id()).map(ownedBlocks::get).orElse(null);
          if (block != null) block.setProfileOrder(order++);
        }
        default -> {}
      }
    }
    cacheEviction.evictByUserId(cmd.userId());
  }

  private static Optional<Long> parseBlockId(String raw) {
    try {
      return Optional.of(Long.parseLong(raw));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }
}
