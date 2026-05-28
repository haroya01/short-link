package com.example.short_link.tag.infrastructure.persistence;

import com.example.short_link.tag.domain.TagEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaTagRepository extends JpaRepository<TagEntity, Long> {

  List<TagEntity> findAllByUserIdOrderByNameAsc(Long userId);

  Optional<TagEntity> findFirstByUserIdAndName(Long userId, String name);

  List<TagEntity> findAllByUserIdAndNameIn(Long userId, List<String> names);

  @Query(
      "SELECT lt.tagId, COUNT(lt.linkId) FROM LinkTagEntity lt "
          + "WHERE lt.tagId IN :tagIds GROUP BY lt.tagId")
  List<Object[]> countLinksByTagIds(@Param("tagIds") List<Long> tagIds);
}
