package com.example.short_link.user.application;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.DateTimeException;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferencesService {

  private final UserRepository userRepository;

  @Transactional
  public UserEntity updateTimezone(Long userId, String timezone) {
    String validated = validateTimezone(timezone);
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    user.changeTimezone(validated);
    return user;
  }

  private static String validateTimezone(String timezone) {
    if (timezone == null) throw new UserException(UserErrorCode.INVALID_TIMEZONE, "null");
    try {
      return ZoneId.of(timezone).getId();
    } catch (DateTimeException e) {
      throw new UserException(UserErrorCode.INVALID_TIMEZONE, timezone);
    }
  }
}
