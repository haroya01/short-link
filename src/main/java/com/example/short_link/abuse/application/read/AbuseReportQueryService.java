package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.application.read.AbuseReportView.SubjectSnapshot;
import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.CommentSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.PostSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.UserSubjectSnapshot;
import com.example.short_link.common.web.PostPublicUrlBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 모더레이션 큐 조회 + 대상 하이드레이션. subjectType 별로 한 번의 배치 쿼리(POST/COMMENT/USER)로 스냅샷을 모아 N+1 없이 뷰를 채운다 —
 * 관리자가 "무엇이 신고됐는지" 보고 바로 이동할 수 있게.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbuseReportQueryService {

  private static final String POST_UNPUBLISHED = "UNPUBLISHED";

  private final AbuseReportRepository abuseReportRepository;
  private final PostPublicUrlBuilder postPublicUrlBuilder;

  public List<AbuseReportView> listAll() {
    return enrichAll(abuseReportRepository.findAllByOrderByCreatedAtDesc());
  }

  public List<AbuseReportView> listByStatus(AbuseReportStatus status) {
    return enrichAll(abuseReportRepository.findAllByStatusOrderByCreatedAtDesc(status));
  }

  /** Enrich a single report — used by the resolve endpoint so its response keeps the snapshot. */
  public AbuseReportView enrich(AbuseReportEntity report) {
    return enrichAll(List.of(report)).get(0);
  }

  private List<AbuseReportView> enrichAll(List<AbuseReportEntity> reports) {
    Map<Long, PostSubjectSnapshot> posts = byPostId(reports);
    Map<Long, CommentSubjectSnapshot> comments = byCommentId(reports);
    Map<Long, UserSubjectSnapshot> users = byUserId(reports);
    return reports.stream()
        .map(r -> AbuseReportView.of(r, snapshotFor(r, posts, comments, users)))
        .toList();
  }

  private Map<Long, PostSubjectSnapshot> byPostId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.POST);
    return ids.isEmpty()
        ? Map.of()
        : abuseReportRepository.findPostSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(PostSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private Map<Long, CommentSubjectSnapshot> byCommentId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.COMMENT);
    return ids.isEmpty()
        ? Map.of()
        : abuseReportRepository.findCommentSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(CommentSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private Map<Long, UserSubjectSnapshot> byUserId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.USER);
    return ids.isEmpty()
        ? Map.of()
        : abuseReportRepository.findUserSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(UserSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private static List<Long> subjectIds(List<AbuseReportEntity> reports, AbuseSubjectType type) {
    return reports.stream()
        .filter(r -> r.getSubjectType() == type)
        .map(AbuseReportEntity::getSubjectId)
        .distinct()
        .toList();
  }

  private SubjectSnapshot snapshotFor(
      AbuseReportEntity report,
      Map<Long, PostSubjectSnapshot> posts,
      Map<Long, CommentSubjectSnapshot> comments,
      Map<Long, UserSubjectSnapshot> users) {
    Long subjectId = report.getSubjectId();
    return switch (report.getSubjectType()) {
      case POST -> fromPost(posts.get(subjectId));
      case COMMENT -> fromComment(comments.get(subjectId));
      case USER -> fromUser(users.get(subjectId));
    };
  }

  private SubjectSnapshot fromPost(PostSubjectSnapshot snapshot) {
    if (snapshot == null) {
      return SubjectSnapshot.EMPTY;
    }
    String url = postPublicUrlBuilder.build(snapshot.getAuthorHandle(), snapshot.getSlug());
    boolean removed = POST_UNPUBLISHED.equals(snapshot.getStatus());
    return new SubjectSnapshot(snapshot.getTitle(), snapshot.getAuthorHandle(), url, null, removed);
  }

  private SubjectSnapshot fromComment(CommentSubjectSnapshot snapshot) {
    if (snapshot == null) {
      return SubjectSnapshot.EMPTY;
    }
    boolean removed = snapshot.getDeleted() != null && snapshot.getDeleted() != 0L;
    return new SubjectSnapshot(
        null, snapshot.getAuthorHandle(), null, snapshot.getExcerpt(), removed);
  }

  private SubjectSnapshot fromUser(UserSubjectSnapshot snapshot) {
    if (snapshot == null) {
      return SubjectSnapshot.EMPTY;
    }
    // 유저 대상: 이미 정지/차단(BANNED/SUSPENDED)됐으면 removed 로 표시해 관리자가 조치 여부를 한눈에 본다.
    boolean removed = !"ACTIVE".equals(snapshot.getModerationStatus());
    return new SubjectSnapshot(null, snapshot.getHandle(), null, null, removed);
  }
}
