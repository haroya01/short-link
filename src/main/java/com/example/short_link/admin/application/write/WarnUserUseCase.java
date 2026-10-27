package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.WarningCopy;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 약관 위반 경고는 사용자의 푸시 수신 설정과 무관하게 전달한다.
@Service
@RequiredArgsConstructor
public class WarnUserUseCase {

  private final UserSummaryReader users;
  private final LinkNotificationDispatcher dispatcher;

  public void execute(long userId, String shortCode, String message) {
    UserSummary user =
        users
            .find(userId)
            .orElseThrow(() -> new AdminException(AdminErrorCode.USER_NOT_FOUND, userId));
    dispatcher.dispatch(
        userId,
        LinkNotificationType.WARNING,
        shortCode,
        WarningCopy.subtitle(user.locale()),
        message);
  }
}
