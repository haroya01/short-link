package com.example.short_link.user.application;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.InvalidTimezoneException;
import com.example.short_link.user.exception.UserNotFoundException;
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
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    user.changeTimezone(validated);
    return user;
  }

  private static String validateTimezone(String timezone) {
    if (timezone == null) throw new InvalidTimezoneException("null");
    try {
      return ZoneId.of(timezone).getId();
    } catch (DateTimeException e) {
      throw new InvalidTimezoneException(timezone);
    }
  }
}
