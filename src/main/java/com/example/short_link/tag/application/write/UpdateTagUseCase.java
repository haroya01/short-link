package com.example.short_link.tag.application.write;

import com.example.short_link.tag.application.dto.TagSummary;
import com.example.short_link.tag.application.helper.TagSanitizer;
import com.example.short_link.tag.application.read.TagQueryService;
import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.TagRepository;
import com.example.short_link.tag.exception.TagErrorCode;
import com.example.short_link.tag.exception.TagException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateTagUseCase {

  private final TagRepository tagRepository;
  private final TagQueryService tagQueryService;

  @Transactional
  public TagSummary execute(Long userId, Long id, String name, String color) {
    TagEntity tag =
        tagRepository
            .findById(id)
            .orElseThrow(() -> new TagException(TagErrorCode.TAG_NOT_FOUND, id));
    if (!tag.getUserId().equals(userId)) throw new TagException(TagErrorCode.TAG_NOT_FOUND, id);
    if (name != null) {
      String trimmed = TagSanitizer.sanitizeName(name);
      if (!trimmed.equals(tag.getName())
          && tagRepository.findFirstByUserIdAndName(userId, trimmed).isPresent()) {
        throw new TagException(TagErrorCode.DUPLICATE_TAG_NAME, trimmed);
      }
      tag.rename(trimmed);
    }
    if (color != null) tag.recolor(TagSanitizer.sanitizeColor(color));
    long count = tagQueryService.countMap(List.of(tag.getId())).getOrDefault(tag.getId(), 0L);
    return new TagSummary(tag.getId(), tag.getName(), tag.getColor(), count, tag.getCreatedAt());
  }
}
