package com.example.short_link.user.application.write;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.dto.MintedAccessToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Uses the authenticated requester's own role, granting no additional privileges. */
@Service
@RequiredArgsConstructor
public class MintAccessTokenUseCase {

  private final UserRepository userRepository;
  private final JwtTokenService jwt;

  @Transactional(readOnly = true)
  public MintedAccessToken mintFor(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    String token = jwt.createAccessToken(user.getId(), user.getRole().name());
    return new MintedAccessToken(token, jwt.accessTtl().toSeconds());
  }
}
