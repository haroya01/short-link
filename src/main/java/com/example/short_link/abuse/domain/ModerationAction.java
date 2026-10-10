package com.example.short_link.abuse.domain;

// 글·댓글·하이라이트 답글 조치는 물리 삭제하지 않는다. 노트는 작성자가 지울 때처럼 지운다(노트에는 복구가 없다). 사용자 정지는 기한까지 쓰기를, 영구 차단은 로그인과
// 쓰기를 차단한다.
public enum ModerationAction {
  NONE,
  UNPUBLISH_POST,
  DELETE_COMMENT,
  SUSPEND_USER,
  BAN_USER,
  DISABLE_LINK,
  DELETE_NOTE,
  DELETE_HIGHLIGHT_REPLY;

  public boolean appliesTo(AbuseSubjectType subjectType) {
    return switch (this) {
      case NONE -> true;
      case UNPUBLISH_POST -> subjectType == AbuseSubjectType.POST;
      case DELETE_COMMENT -> subjectType == AbuseSubjectType.COMMENT;
      case SUSPEND_USER, BAN_USER -> subjectType == AbuseSubjectType.USER;
      case DISABLE_LINK -> subjectType == AbuseSubjectType.LINK;
      case DELETE_NOTE -> subjectType == AbuseSubjectType.NOTE;
      case DELETE_HIGHLIGHT_REPLY -> subjectType == AbuseSubjectType.HIGHLIGHT_REPLY;
    };
  }
}
