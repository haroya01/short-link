package com.example.short_link.user.application.read;

import com.example.short_link.link.application.LinkAccountData;
import com.example.short_link.user.application.dto.UserDataExport;
import com.example.short_link.user.application.dto.UserDataExport.ExportedUser;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Exports account, links, and masked click statistics. Blog/social data is excluded and provided
// through the privacy policy's contact channel; policy §8 must stay aligned with this scope.
@Service
@RequiredArgsConstructor
public class UserDataExportService {

  private final UserRepository userRepository;
  private final LinkAccountData linkAccountData;

  @Transactional(readOnly = true)
  public UserDataExport export(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

    LinkAccountData.OwnedLinks links = linkAccountData.export(userId);
    return new UserDataExport(ExportedUser.from(user), links.links(), links.clicks());
  }
}
