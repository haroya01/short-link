package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagPrefQueryService {

  private final UserTagPrefRepository repository;

  public TagPrefsView get(Long userId) {
    List<UserTagPrefEntity> prefs = repository.findAllByUserId(userId);
    List<String> followed =
        prefs.stream()
            .filter(p -> p.getKind() == TagPrefKind.FOLLOW)
            .map(UserTagPrefEntity::getTag)
            .toList();
    List<String> hidden =
        prefs.stream()
            .filter(p -> p.getKind() == TagPrefKind.HIDE)
            .map(UserTagPrefEntity::getTag)
            .toList();
    return new TagPrefsView(followed, hidden);
  }
}
