package com.example.short_link.tag.application.write;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.tag.application.helper.TagSanitizer;
import com.example.short_link.tag.domain.LinkTagEntity;
import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.LinkTagRepository;
import com.example.short_link.tag.domain.repository.TagRepository;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplaceLinkTagsUseCase {

  private final LinkOwnership ownership;
  private final TagRepository tagRepository;
  private final LinkTagRepository linkTagRepository;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional
  public List<String> execute(Long userId, ShortCode shortCode, List<String> rawNames) {
    LinkId linkId = ownership.requireOwnedId(userId, shortCode);

    List<String> normalized = normalize(rawNames);
    if (normalized.size() > TagSanitizer.MAX_TAGS_PER_LINK) {
      throw new IllegalArgumentException(
          "too many tags (max " + TagSanitizer.MAX_TAGS_PER_LINK + ")");
    }

    linkTagRepository.deleteByLinkId(linkId.value());
    linkCacheEviction.evictAfterCommit(shortCode);
    if (normalized.isEmpty()) return List.of();

    Collator sameName = sameNameCollator();
    List<TagEntity> tags =
        new ArrayList<>(tagRepository.findAllByUserIdAndNameIn(userId, normalized));
    List<String> applied = new ArrayList<>();
    for (String name : normalized) {
      TagEntity tag =
          tags.stream().filter(t -> sameName.equals(t.getName(), name)).findFirst().orElse(null);
      if (tag == null) {
        tag = tagRepository.save(new TagEntity(userId, name, null));
        tags.add(tag);
      } else if (applied.contains(tag.getName())) {
        continue;
      }
      linkTagRepository.save(new LinkTagEntity(linkId, tag.getId()));
      applied.add(tag.getName());
    }
    return applied;
  }

  private static Collator sameNameCollator() {
    Collator collator = Collator.getInstance(Locale.ROOT);
    collator.setStrength(Collator.PRIMARY);
    return collator;
  }

  private static List<String> normalize(List<String> raw) {
    if (raw == null) return List.of();
    Set<String> seen = new HashSet<>();
    List<String> out = new ArrayList<>();
    for (String n : raw) {
      if (n == null) continue;
      String trimmed = n.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.length() > TagSanitizer.MAX_NAME_LENGTH) {
        throw new IllegalArgumentException(
            "tag name too long (max " + TagSanitizer.MAX_NAME_LENGTH + ")");
      }
      if (seen.add(trimmed)) out.add(trimmed);
    }
    return out;
  }
}
