package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.domain.LinkTagEntity;
import com.example.short_link.link.domain.LinkTagRepository;
import com.example.short_link.link.domain.TagEntity;
import com.example.short_link.link.domain.TagRepository;
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
 * Sets/queries the tags attached to a single link. {@link #replaceTags(Long, String, List)} is a
 * full replace — it auto-creates missing tags so the UI can let users type a new tag inline without
 * a separate \"create tag\" step.
 */
@Service
@RequiredArgsConstructor
public class LinkTagService {

  public static final int MAX_TAGS_PER_LINK = 20;

  private final LinkRepository linkRepository;
  private final TagRepository tagRepository;
  private final LinkTagRepository linkTagRepository;

  @Transactional(readOnly = true)
  public List<String> tagNamesFor(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);
    List<Long> tagIds =
        linkTagRepository.findAllByLinkId(link.getId()).stream()
            .map(LinkTagEntity::getTagId)
            .toList();
    if (tagIds.isEmpty()) return List.of();
    return tagRepository.findAllById(tagIds).stream().map(TagEntity::getName).sorted().toList();
  }

  @Transactional(readOnly = true)
  public Map<Long, List<String>> tagNamesByLinkIds(List<Long> linkIds) {
    if (linkIds.isEmpty()) return Map.of();
    List<LinkTagEntity> joins = linkTagRepository.findAllByLinkIdIn(linkIds);
    if (joins.isEmpty()) return Map.of();
    Set<Long> tagIds = new HashSet<>();
    for (LinkTagEntity j : joins) tagIds.add(j.getTagId());
    Map<Long, String> tagNames = new HashMap<>();
    for (TagEntity t : tagRepository.findAllById(tagIds)) {
      tagNames.put(t.getId(), t.getName());
    }
    Map<Long, List<String>> out = new HashMap<>();
    for (LinkTagEntity j : joins) {
      out.computeIfAbsent(j.getLinkId(), k -> new java.util.ArrayList<>())
          .add(tagNames.get(j.getTagId()));
    }
    out.values().forEach(java.util.Collections::sort);
    return out;
  }

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public List<String> replaceTags(Long userId, String shortCode, List<String> rawNames) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);

    List<String> normalized = normalize(rawNames);
    if (normalized.size() > MAX_TAGS_PER_LINK) {
      throw new IllegalArgumentException("too many tags (max " + MAX_TAGS_PER_LINK + ")");
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

  private List<String> normalize(List<String> raw) {
    if (raw == null) return List.of();
    Set<String> seen = new HashSet<>();
    List<String> out = new java.util.ArrayList<>();
    for (String n : raw) {
      if (n == null) continue;
      String trimmed = n.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.length() > TagService.MAX_NAME_LENGTH) {
        throw new IllegalArgumentException(
            "tag name too long (max " + TagService.MAX_NAME_LENGTH + ")");
      }
      if (seen.add(trimmed)) out.add(trimmed);
    }
    return out;
  }
}
