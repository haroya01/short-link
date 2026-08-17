package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.WarningCopy;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 약관 7조의 "사유 통지"를 나르는 운영자 경고 — 링크 알림 인박스에 남고 푸시 설정과 무관하게 푸시된다. 부제는 수신자 locale, 본문은 운영자가 쓴 그대로. */
@Service
@RequiredArgsConstructor
public class WarnUserUseCase {

  private final UserRepository users;
  private final LinkNotificationDispatcher dispatcher;

  public void execute(long userId, String shortCode, String message) {
    UserEntity user =
        users
            .findById(userId)
            .orElseThrow(() -> new AdminException(AdminErrorCode.USER_NOT_FOUND, userId));
    dispatcher.dispatch(
        userId,
        LinkNotificationType.WARNING,
        shortCode,
        WarningCopy.subtitle(user.getLocale()),
        message);
  }
}
