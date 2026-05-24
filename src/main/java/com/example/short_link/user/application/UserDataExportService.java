package com.example.short_link.user.application;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.application.dto.UserDataExport;
import com.example.short_link.user.application.dto.UserDataExport.ExportedClick;
import com.example.short_link.user.application.dto.UserDataExport.ExportedLink;
import com.example.short_link.user.application.dto.UserDataExport.ExportedUser;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.example.short_link.user.exception.UserNotFoundException;
import java.util.List;
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
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

    List<LinkEntity> links = linkRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<ExportedLink> exportedLinks =
        links.stream()
            .map(
                l ->
                    new ExportedLink(
                        l.getShortCode(),
                        l.getOriginalUrl(),
                        l.getCreatedAt(),
                        l.getExpiresAt(),
                        l.getOgTitle(),
                        l.getOgDescription(),
                        l.getOgImage()))
            .toList();

    List<ExportedClick> exportedClicks = List.of();
    if (!links.isEmpty()) {
      List<Long> linkIds = links.stream().map(LinkEntity::getId).toList();
      List<ClickEventEntity> events =
          clickEventRepository.findAllByLinkIdInOrderByClickedAtAsc(linkIds);
      exportedClicks =
          events.stream()
              .map(
                  c -> {
                    String shortCode =
                        links.stream()
                            .filter(l -> l.getId().equals(c.getLinkId()))
                            .map(LinkEntity::getShortCode)
                            .findFirst()
                            .orElse(null);
                    return new ExportedClick(
                        shortCode,
                        c.getClickedAt(),
                        c.getReferrerHost(),
                        c.getDeviceClass(),
                        c.getOsName(),
                        c.getBrowserName(),
                        c.getCountryCode(),
                        c.getRegionName(),
                        c.getCityName(),
                        c.getLanguage(),
                        c.isBot(),
                        c.getBotName());
                  })
              .toList();
    }

    return new UserDataExport(
        new ExportedUser(
            user.getId(),
            user.getEmail(),
            user.getOauthProvider(),
            user.getRole().name(),
            user.getTimezone(),
            user.getCreatedAt()),
        exportedLinks,
        exportedClicks);
  }
}
