package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkTagRepository;
import com.example.short_link.link.domain.TagEntity;
import com.example.short_link.link.domain.TagRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user tag CRUD. Names are case-sensitive but trimmed; uniqueness is enforced by the (user_id,
 * name) DB constraint and double-checked in the service to give a friendly error code instead of a
 * raw integrity violation.
 */
@Service
@RequiredArgsConstructor
public class TagService {

  public static final int MAX_NAME_LENGTH = 50;

  private final TagRepository tagRepository;
  private final LinkTagRepository linkTagRepository;

  @Transactional(readOnly = true)
  public List<TagSummary> list(Long userId) {
    List<TagEntity> tags = tagRepository.findAllByUserIdOrderByNameAsc(userId);
    if (tags.isEmpty()) return List.of();
    Map<Long, Long> counts = countMap(tags.stream().map(TagEntity::getId).toList());
    return tags.stream()
        .map(
            t ->
                new TagSummary(
                    t.getId(),
                    t.getName(),
                    t.getColor(),
                    counts.getOrDefault(t.getId(), 0L),
                    t.getCreatedAt()))
        .toList();
  }

  @Transactional
  public TagSummary create(Long userId, String name, String color) {
    String trimmed = sanitizeName(name);
    if (tagRepository.findFirstByUserIdAndName(userId, trimmed).isPresent()) {
      throw new DuplicateTagNameException(trimmed);
    }
    TagEntity saved = tagRepository.save(new TagEntity(userId, trimmed, sanitizeColor(color)));
    return new TagSummary(
        saved.getId(), saved.getName(), saved.getColor(), 0L, saved.getCreatedAt());
  }

  @Transactional
  public TagSummary update(Long userId, Long id, String name, String color) {
    TagEntity tag = tagRepository.findById(id).orElseThrow(() -> new TagNotFoundException(id));
    if (!tag.getUserId().equals(userId)) throw new TagNotFoundException(id);
    if (name != null) {
      String trimmed = sanitizeName(name);
      if (!trimmed.equals(tag.getName())
          && tagRepository.findFirstByUserIdAndName(userId, trimmed).isPresent()) {
        throw new DuplicateTagNameException(trimmed);
      }
      tag.rename(trimmed);
    }
    if (color != null) tag.recolor(sanitizeColor(color));
    long count = countMap(List.of(tag.getId())).getOrDefault(tag.getId(), 0L);
    return new TagSummary(tag.getId(), tag.getName(), tag.getColor(), count, tag.getCreatedAt());
  }

  @Transactional
  public void delete(Long userId, Long id) {
    TagEntity tag = tagRepository.findById(id).orElseThrow(() -> new TagNotFoundException(id));
    if (!tag.getUserId().equals(userId)) throw new TagNotFoundException(id);
    linkTagRepository.deleteByTagId(id);
    tagRepository.delete(tag);
  }

  private Map<Long, Long> countMap(List<Long> tagIds) {
    Map<Long, Long> counts = new HashMap<>();
    if (tagIds.isEmpty()) return counts;
    for (Object[] row : tagRepository.countLinksByTagIds(tagIds)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }

  static String sanitizeName(String name) {
    if (name == null) throw new IllegalArgumentException("tag name required");
    String trimmed = name.trim();
    if (trimmed.isEmpty()) throw new IllegalArgumentException("tag name required");
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("tag name too long (max " + MAX_NAME_LENGTH + ")");
    }
    return trimmed;
  }

  static String sanitizeColor(String color) {
    if (color == null) return null;
    String trimmed = color.trim();
    if (trimmed.isEmpty()) return null;
    if (!trimmed.matches("^#[0-9a-fA-F]{6}$")) {
      throw new IllegalArgumentException("color must be #RRGGBB");
    }
    return trimmed.toLowerCase();
  }

  public record TagSummary(Long id, String name, String color, long linkCount, Instant createdAt) {}
}
