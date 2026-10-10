package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.application.read.AbuseReportView.SubjectSnapshot;
import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.CommentSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.LinkSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.NoteSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.PostSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.UserSubjectSnapshot;
import com.example.short_link.common.web.PostPublicUrlBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbuseReportQueryService {

  private static final String POST_UNPUBLISHED = "UNPUBLISHED";

  private final AbuseReportRepository abuseReportRepository;
  private final AbuseSubjectReader subjects;
  private final PostPublicUrlBuilder postPublicUrlBuilder;

  public List<AbuseReportView> listAll() {
    return enrichAll(abuseReportRepository.findAllByOrderByCreatedAtDesc());
  }

  public List<AbuseReportView> listByStatus(AbuseReportStatus status) {
    return enrichAll(abuseReportRepository.findAllByStatusOrderByCreatedAtDesc(status));
  }

  public AbuseReportView enrich(AbuseReportEntity report) {
    return enrichAll(List.of(report)).get(0);
  }

  private List<AbuseReportView> enrichAll(List<AbuseReportEntity> reports) {
    Map<Long, PostSubjectSnapshot> posts = byPostId(reports);
    Map<Long, CommentSubjectSnapshot> comments = byCommentId(reports);
    Map<Long, UserSubjectSnapshot> users = byUserId(reports);
    Map<Long, LinkSubjectSnapshot> links = byLinkId(reports);
    Map<Long, NoteSubjectSnapshot> notes = byNoteId(reports);
    Map<Long, CommentSubjectSnapshot> highlightReplies = byHighlightReplyId(reports);
    return reports.stream()
        .map(
            r ->
                AbuseReportView.of(
                    r, snapshotFor(r, posts, comments, users, links, notes, highlightReplies)))
        .toList();
  }

  private Map<Long, PostSubjectSnapshot> byPostId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.POST);
    return ids.isEmpty()
        ? Map.of()
        : subjects.findPostSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(PostSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private Map<Long, CommentSubjectSnapshot> byCommentId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.COMMENT);
    return ids.isEmpty()
        ? Map.of()
        : subjects.findCommentSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(CommentSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private Map<Long, CommentSubjectSnapshot> byHighlightReplyId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.HIGHLIGHT_REPLY);
    return ids.isEmpty()
        ? Map.of()
        : subjects.findHighlightReplySubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(CommentSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private Map<Long, UserSubjectSnapshot> byUserId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.USER);
    return ids.isEmpty()
        ? Map.of()
        : subjects.findUserSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(UserSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private Map<Long, LinkSubjectSnapshot> byLinkId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.LINK);
    return ids.isEmpty()
        ? Map.of()
        : subjects.findLinkSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(LinkSubjectSnapshot::getSubjectId, Function.identity()));
  }

  private Map<Long, NoteSubjectSnapshot> byNoteId(List<AbuseReportEntity> reports) {
    List<Long> ids = subjectIds(reports, AbuseSubjectType.NOTE);
    return ids.isEmpty()
        ? Map.of()
        : subjects.findNoteSubjectSnapshots(ids).stream()
            .collect(Collectors.toMap(NoteSubjectSnapshot::getSubjectId, Function.identity()));
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
      Map<Long, UserSubjectSnapshot> users,
      Map<Long, LinkSubjectSnapshot> links,
      Map<Long, NoteSubjectSnapshot> notes,
      Map<Long, CommentSubjectSnapshot> highlightReplies) {
    Long subjectId = report.getSubjectId();
    return switch (report.getSubjectType()) {
      case POST -> fromPost(posts.get(subjectId));
      case COMMENT -> fromComment(comments.get(subjectId));
      case USER -> fromUser(users.get(subjectId));
      case LINK -> fromLink(links.get(subjectId));
      case NOTE -> fromNote(notes.get(subjectId));
      case HIGHLIGHT_REPLY -> fromComment(highlightReplies.get(subjectId));
    };
  }

  // The destination goes in the excerpt, not url, so the admin screen never links to it.
  private SubjectSnapshot fromLink(LinkSubjectSnapshot snapshot) {
    if (snapshot == null) {
      return SubjectSnapshot.EMPTY;
    }
    boolean disabled = snapshot.getDisabled() != null && snapshot.getDisabled() != 0L;
    return new SubjectSnapshot(
        snapshot.getShortCode(),
        snapshot.getOwnerHandle(),
        null,
        snapshot.getOriginalUrl(),
        disabled);
  }

  private SubjectSnapshot fromPost(PostSubjectSnapshot snapshot) {
    if (snapshot == null) {
      return SubjectSnapshot.EMPTY;
    }
    String url = postPublicUrlBuilder.build(snapshot.getAuthorHandle(), snapshot.getSlug());
    boolean removed = POST_UNPUBLISHED.equals(snapshot.getStatus());
    return new SubjectSnapshot(snapshot.getTitle(), snapshot.getAuthorHandle(), url, null, removed);
  }

  // A note taken down is gone (notes are deleted, not hidden), so a missing row reads as removed.
  private static SubjectSnapshot fromNote(NoteSubjectSnapshot snapshot) {
    if (snapshot == null) {
      return new SubjectSnapshot(null, null, null, null, true);
    }
    return new SubjectSnapshot(
        null, snapshot.getAuthorHandle(), null, snapshot.getExcerpt(), false);
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
    // 사용자 제재 상태도 대상 삭제 여부와 같은 removed 필드로 표현한다.
    boolean removed = !"ACTIVE".equals(snapshot.getModerationStatus());
    return new SubjectSnapshot(null, snapshot.getHandle(), null, null, removed);
  }
}
