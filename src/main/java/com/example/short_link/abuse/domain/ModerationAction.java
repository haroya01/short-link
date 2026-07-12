package com.example.short_link.abuse.domain;

/**
 * 신고 처리 시 관리자가 함께 집행하는 조치. resolve 와 같은 트랜잭션에서 대상 슬라이스에 실제 조치가 적용된다.
 *
 * <ul>
 *   <li>{@code NONE} — 상태만 전이(집행 없음). 반려/무혐의.
 *   <li>{@code UNPUBLISH_POST} — POST 대상: 글 게시 취소(soft, 복구 가능).
 *   <li>{@code DELETE_COMMENT} — COMMENT 대상: 댓글 soft 삭제(공개 조회에서 숨김).
 *   <li>{@code SUSPEND_USER} — USER 대상: 임시 정지(만료시각까지 쓰기 차단).
 *   <li>{@code BAN_USER} — USER 대상: 영구 차단(로그인·쓰기 차단).
 * </ul>
 */
public enum ModerationAction {
  NONE,
  UNPUBLISH_POST,
  DELETE_COMMENT,
  SUSPEND_USER,
  BAN_USER;

  /** 이 조치가 적용 가능한 대상 종류 — 불일치 조치(예: COMMENT 에 BAN_USER)를 거른다. */
  public boolean appliesTo(AbuseSubjectType subjectType) {
    return switch (this) {
      case NONE -> true;
      case UNPUBLISH_POST -> subjectType == AbuseSubjectType.POST;
      case DELETE_COMMENT -> subjectType == AbuseSubjectType.COMMENT;
      case SUSPEND_USER, BAN_USER -> subjectType == AbuseSubjectType.USER;
    };
  }
}
