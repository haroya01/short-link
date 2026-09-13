package com.example.short_link.post.presentation;

import com.example.short_link.common.web.ClientIp;
import com.example.short_link.post.application.write.RecordPostViewCommand;
import com.example.short_link.post.application.write.RecordPostViewUseCase;
import com.example.short_link.post.application.write.ViewContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 없는 글과 미발행 글의 비콘은 무시한다. 조회는 중복 제거 없이 집계한다. */
@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicPostViewBeaconController {

  private final RecordPostViewUseCase recordPostView;

  @PostMapping("/{username}/posts/{slug}/view")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void recordView(
      @PathVariable String username,
      @PathVariable String slug,
      @RequestParam(value = "src", required = false) String src,
      @RequestParam(value = "ref", required = false) String ref,
      @RequestParam(value = "sid", required = false) String sid,
      @RequestParam(value = "utm_source", required = false) String utmSource,
      @RequestParam(value = "utm_medium", required = false) String utmMedium,
      @RequestParam(value = "utm_campaign", required = false) String utmCampaign,
      @RequestParam(value = "utm_term", required = false) String utmTerm,
      @RequestParam(value = "utm_content", required = false) String utmContent,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    recordPostView.execute(
        new RecordPostViewCommand(username, slug),
        new ViewContext(
            // fetch의 Referer는 글 자체이므로 document.referrer를 담은 ref 쿼리를 우선한다.
            ref != null && !ref.isBlank() ? ref : referrer,
            userAgent,
            ClientIp.of(req),
            acceptLanguage,
            src,
            utmSource,
            utmMedium,
            utmCampaign,
            utmTerm,
            utmContent,
            "1".equals(req.getHeader("Sec-GPC")),
            // behavior_event와 조인하는 탭 수명 세션 ID다.
            sid));
  }
}
