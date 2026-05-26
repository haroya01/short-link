package com.example.short_link.tag.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full replace — missing tags are auto-created so the UI can let users type a new tag inline
 * without a separate "create tag" step.
 */
@Service
@RequiredArgsConstructor
public class ReplaceLinkTagsUseCase {

  private final LinkRepository linkRepository;
  private final TagRepository tagRepository;
  private final LinkTagRepository linkTagRepository;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public List<String> execute(Long userId, ShortCode shortCode, List<String> rawNames) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);

    List<String> normalized = normalize(rawNames);
    if (normalized.size() > TagSanitizer.MAX_TAGS_PER_LINK) {
      throw new IllegalArgumentException(
          "too many tags (max " + TagSanitizer.MAX_TAGS_PER_LINK + ")");
    }

    linkTagRepository.deleteByLinkId(link.getId());
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
      linkTagRepository.save(new LinkTagEntity(link.getId(), tag.getId()));
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
