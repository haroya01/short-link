package com.example.short_link.user.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.ClickEventRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.dto.UserDataExport;
import com.example.short_link.user.application.dto.UserDataExport.ExportedClick;
import com.example.short_link.user.application.dto.UserDataExport.ExportedLink;
import com.example.short_link.user.application.dto.UserDataExport.ExportedUser;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR Article 20 (data portability): returns a JSON snapshot of all data we hold about the user.
 * Click events use the masked client_ip (already stored that way), so no de-anonymization happens
 * via export.
 */
@Service
@RequiredArgsConstructor
public class UserDataExportService {

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final ClickEventRepository clickEventRepository;

  @Transactional(readOnly = true)
  public UserDataExport export(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

    List<LinkEntity> links = linkRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<ExportedLink> exportedLinks = links.stream().map(ExportedLink::from).toList();
    List<ExportedClick> exportedClicks = exportClicks(links);

    return new UserDataExport(ExportedUser.from(user), exportedLinks, exportedClicks);
  }

  private List<ExportedClick> exportClicks(List<LinkEntity> links) {
    if (links.isEmpty()) return List.of();
    // O(1) shortCode lookup — previously each click did a links.stream().filter().findFirst()
    // (O(N×M) over the whole user's click history).
    Map<Long, String> shortCodeByLinkId =
        links.stream().collect(Collectors.toMap(LinkEntity::getId, LinkEntity::getShortCode));
    List<Long> linkIds = List.copyOf(shortCodeByLinkId.keySet());
    return clickEventRepository.findAllByLinkIdInOrderByClickedAtAsc(linkIds).stream()
        .map(c -> ExportedClick.from(c, shortCodeByLinkId.get(c.getLinkId())))
        .toList();
  }
}
