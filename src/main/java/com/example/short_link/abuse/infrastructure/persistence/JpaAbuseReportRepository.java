package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.PostSubjectSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAbuseReportRepository extends JpaRepository<AbuseReportEntity, Long> {

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();

  @Query(
      value =
          "SELECT p.id AS subjectId, p.title AS title, p.slug AS slug, p.status AS status, "
              + "u.username AS authorHandle "
              + "FROM posts p LEFT JOIN users u ON u.id = p.user_id "
              + "WHERE p.id IN (:postIds)",
      nativeQuery = true)
  List<PostSubjectSnapshot> findPostSubjectSnapshots(@Param("postIds") Collection<Long> postIds);
}
