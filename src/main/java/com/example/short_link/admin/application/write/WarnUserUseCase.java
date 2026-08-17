package com.example.short_link.admin.application.write;

import com.example.short_link.admin.domain.repository.AdminBrowseRepository;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 약관 7조의 "사유 통지"를 나르는 운영자 경고 — 링크 알림 인박스에 남고 푸시 설정과 무관하게 푸시된다. */
@Service
@RequiredArgsConstructor
public class WarnUserUseCase {

  private final AdminBrowseRepository users;
  private final LinkNotificationDispatcher dispatcher;

  public void execute(long userId, String shortCode, String message) {
    if (users.findUser(userId).isEmpty()) {
      throw new AdminException(AdminErrorCode.USER_NOT_FOUND, userId);
    }
    dispatcher.dispatch(userId, LinkNotificationType.WARNING, shortCode, "운영자 경고", message);
  }
}
