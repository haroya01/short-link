package com.example.short_link.tag.application.read;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.read.LinkTagLookup;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.tag.domain.LinkTagEntity;
import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.LinkTagRepository;
import com.example.short_link.tag.domain.repository.TagRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkTagQueryService implements LinkTagLookup {

  private final LinkRepository linkRepository;
  private final TagRepository tagRepository;
  private final LinkTagRepository linkTagRepository;
  private final LinkAccessGuard accessGuard;

  @Override
  @Transactional(readOnly = true)
  public List<String> tagNamesFor(Long userId, ShortCode shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    accessGuard.requireView(userId, link);
    List<Long> tagIds =
        linkTagRepository.findAllByLinkId(link.linkId().value()).stream()
            .map(LinkTagEntity::getTagId)
            .toList();
    if (tagIds.isEmpty()) return List.of();
    return tagRepository.findAllById(tagIds).stream().map(TagEntity::getName).sorted().toList();
  }

  @Override
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
      out.computeIfAbsent(j.getLinkId(), k -> new ArrayList<>()).add(tagNames.get(j.getTagId()));
    }
    out.values().forEach(Collections::sort);
    return out;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<List<Long>> linkIdsForTag(Long userId, String tagName) {
    if (tagName == null || tagName.isBlank()) return Optional.empty();
    List<Long> linkIds =
        tagRepository
            .findFirstByUserIdAndName(userId, tagName.trim())
            .map(
                tag ->
                    linkTagRepository.findAllByTagId(tag.getId()).stream()
                        .map(LinkTagEntity::getLinkId)
                        .toList())
            .orElse(List.of());
    return Optional.of(linkIds);
  }
}
