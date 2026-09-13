package com.example.short_link.abuse.domain;

/** 기존 자유서술 신고는 사유 코드가 없어 null로 유지한다. */
public enum AbuseReason {
  SPAM,
  HARASSMENT,
  VIOLENCE,
  SEXUAL,
  COPYRIGHT,
  OTHER
}
