package com.example.short_link.tag.application.read;

import com.example.short_link.tag.application.dto.TagSummary;
import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.TagRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagQueryService {

  private final TagRepository tagRepository;

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

  public Map<Long, Long> countMap(List<Long> tagIds) {
    Map<Long, Long> counts = new HashMap<>();
    if (tagIds.isEmpty()) return counts;
    for (Object[] row : tagRepository.countLinksByTagIds(tagIds)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }
}
