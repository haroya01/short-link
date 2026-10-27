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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    Map<String, TagEntity> existing = new HashMap<>();
    for (TagEntity t : tagRepository.findAllByUserIdAndNameIn(userId, normalized)) {
      existing.put(t.getName(), t);
    }
    for (String name : normalized) {
      TagEntity tag = existing.get(name);
      if (tag == null) {
        tag = tagRepository.save(new TagEntity(userId, name, null));
        existing.put(name, tag);
      }
      linkTagRepository.save(new LinkTagEntity(linkId, tag.getId()));
    }
    return normalized;
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
