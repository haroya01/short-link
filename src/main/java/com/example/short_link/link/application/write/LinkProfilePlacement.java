package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkProfilePlacement {

  private final LinkOwnership ownership;
  private final LinkRepository links;

  public ProfileLinkSlot requireOwn(Long userId, ShortCode shortCode) {
    return new ProfileLinkSlot(ownership.requireOwnedHidingOthers(userId, shortCode));
  }

  public int placedCount(Long userId) {
    return links.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId).size();
  }

  public Map<String, ProfileLinkSlot> placedSlots(Long userId) {
    Map<String, ProfileLinkSlot> slots = new LinkedHashMap<>();
    for (LinkEntity link :
        links.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId)) {
      slots.put(link.getShortCode().value(), new ProfileLinkSlot(link));
    }
    return slots;
  }

  public void unfeatureAll(Long userId) {
    for (LinkEntity link : links.findAllByUserIdAndProfileHighlightedIsTrue(userId)) {
      link.unfeatureOnProfile();
    }
  }

  public void unfeatureAllBut(Long userId, LinkId kept) {
    for (LinkEntity link : links.findAllByUserIdAndProfileHighlightedIsTrue(userId)) {
      if (!link.linkId().equals(kept)) {
        link.unfeatureOnProfile();
      }
    }
  }
}
