package com.example.short_link.tag.application.write;

import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.LinkTagRepository;
import com.example.short_link.tag.domain.repository.TagRepository;
import com.example.short_link.tag.exception.TagErrorCode;
import com.example.short_link.tag.exception.TagException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteTagUseCase {

  private final TagRepository tagRepository;
  private final LinkTagRepository linkTagRepository;

  @Transactional
  public void execute(Long userId, Long id) {
    TagEntity tag =
        tagRepository
            .findById(id)
            .orElseThrow(() -> new TagException(TagErrorCode.TAG_NOT_FOUND, id));
    if (!tag.getUserId().equals(userId)) throw new TagException(TagErrorCode.TAG_NOT_FOUND, id);
    linkTagRepository.deleteByTagId(id);
    tagRepository.delete(tag);
  }
}
