package com.example.short_link.tag.application.write;

import com.example.short_link.tag.application.dto.TagSummary;
import com.example.short_link.tag.application.helper.TagSanitizer;
import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.TagRepository;
import com.example.short_link.tag.exception.TagErrorCode;
import com.example.short_link.tag.exception.TagException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateTagUseCase {

  private final TagRepository tagRepository;

  @Transactional
  public TagSummary execute(Long userId, String name, String color) {
    String trimmed = TagSanitizer.sanitizeName(name);
    if (tagRepository.findFirstByUserIdAndName(userId, trimmed).isPresent()) {
      throw new TagException(TagErrorCode.DUPLICATE_TAG_NAME, trimmed);
    }
    TagEntity saved =
        tagRepository.save(new TagEntity(userId, trimmed, TagSanitizer.sanitizeColor(color)));
    return new TagSummary(
        saved.getId(), saved.getName(), saved.getColor(), 0L, saved.getCreatedAt());
  }
}
