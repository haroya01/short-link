package com.example.short_link.admin.application.read;

import com.example.short_link.abuse.application.read.LinkReportReader;
import com.example.short_link.abuse.application.read.LinkReports;
import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.admin.application.helper.PreviewThumbnail;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.common.net.PublicImageFetcher;
import com.example.short_link.common.net.PublicImageFetcher.Image;
import com.example.short_link.common.net.PublicImageFetcher.Refused;
import com.example.short_link.link.application.dto.LinkEvidence;
import com.example.short_link.link.application.read.LinkEvidenceReader;
import com.example.short_link.link.domain.ShortCode;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminLinkPreviewService {

  static final int MAX_BYTES = 5 * 1024 * 1024;

  private final LinkEvidenceReader links;
  private final LinkReportReader reports;
  private final PublicImageFetcher fetcher;
  private final AuditLogService auditLog;

  public static boolean imageWithheld(LinkReports reports) {
    return reports.includes(AbuseReason.SEXUAL);
  }

  public byte[] preview(ShortCode shortCode, boolean clear, Long adminUserId) {
    LinkEvidence.Image image =
        links
            .image(shortCode)
            .orElseThrow(
                () -> new AdminException(AdminErrorCode.LINK_NOT_FOUND, shortCode.value()));
    if (imageWithheld(reports.about(image.linkId()))) {
      throw new AdminException(AdminErrorCode.LINK_PREVIEW_WITHHELD, shortCode.value());
    }
    if (image.url() == null) {
      throw unavailable(shortCode, Unavailable.NO_IMAGE, "no image");
    }
    byte[] rendered =
        switch (fetcher.fetch(image.url(), MAX_BYTES)) {
          case Refused refused ->
              throw unavailable(shortCode, Unavailable.FETCH_REFUSED, refused.reason());
          case Image fetched -> render(shortCode, fetched.bytes(), clear);
        };
    if (clear) {
      auditLog.record(
          AuditAction.LINK_PREVIEW_CLEAR_VIEWED, "link", shortCode.value(), adminUserId, Map.of());
    }
    return rendered;
  }

  private static byte[] render(ShortCode shortCode, byte[] source, boolean clear) {
    try {
      return PreviewThumbnail.render(source, clear);
    } catch (PreviewThumbnail.TooManyPixels tooLarge) {
      throw unavailable(shortCode, Unavailable.TOO_MANY_PIXELS, tooLarge.getMessage());
    } catch (IOException unreadable) {
      throw unavailable(shortCode, Unavailable.UNDECODABLE, unreadable.getMessage());
    }
  }

  private static AdminException unavailable(ShortCode shortCode, Unavailable why, String detail) {
    return new AdminException(AdminErrorCode.LINK_PREVIEW_UNAVAILABLE, shortCode.value(), detail)
        .with("reason", why.name());
  }

  enum Unavailable {
    NO_IMAGE,
    FETCH_REFUSED,
    TOO_MANY_PIXELS,
    UNDECODABLE
  }
}
