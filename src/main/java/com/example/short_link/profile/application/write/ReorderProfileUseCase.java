package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkProfileBindingEntity;
import com.example.short_link.link.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reorders the profile feed (links + text/divider blocks) to the exact sequence given. Items the
 * user doesn't own are silently skipped — defensive against the front sending stale tokens after a
 * delete. Items not in the list keep their existing profile_order (so a featured link not mentioned
 * stays where it is, off the profile).
 */
@Service
@RequiredArgsConstructor
public class ReorderProfileUseCase {

  private final LinkRepository linkRepository;
  private final LinkProfileBindingRepository profileBindingRepository;
  private final ProfileBlockRepository profileBlockRepository;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  public void execute(ReorderProfileCommand cmd) {
    Map<String, LinkEntity> ownedLinks = new HashMap<>();
    for (var link :
        linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(
            cmd.userId())) {
      ownedLinks.put(link.getShortCode(), link);
    }
    Map<Long, ProfileBlockEntity> ownedBlocks = new HashMap<>();
    for (var block : profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(cmd.userId())) {
      ownedBlocks.put(block.getId(), block);
    }
    int order = 1;
    for (ReorderItem item : cmd.items()) {
      if (item == null || item.kind() == null || item.id() == null) continue;
      switch (item.kind().toUpperCase()) {
        case "LINK" -> {
          LinkEntity link = ownedLinks.get(item.id());
          if (link != null) {
            int next = order++;
            link.setProfileOrder(next);
            LinkProfileBindingEntity binding =
                profileBindingRepository
                    .findById(link.getId())
                    .orElseGet(() -> new LinkProfileBindingEntity(link.getId()));
            binding.changeProfileOrder(next);
            profileBindingRepository.save(binding);
          }
        }
        case "BLOCK" -> {
          ProfileBlockEntity block = parseBlockId(item.id()).map(ownedBlocks::get).orElse(null);
          if (block != null) block.setProfileOrder(order++);
        }
        default -> {
          /* unknown kind — skip */
        }
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
