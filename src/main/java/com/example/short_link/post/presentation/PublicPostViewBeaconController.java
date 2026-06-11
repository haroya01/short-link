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

/**
 * Public post 의 view beacon. 인증 없이 frontend 가 페이지 로드 시 fire. invalid username/slug 또는 non-PUBLISHED
 * 면 silent (UseCase 가 noop). dedup 없음 — v0 minimum. 요청 헤더/쿼리(referrer·UA·IP·UTM·src)를 그대로 넘겨 이벤트를
 * enrich 한다(글별/시리즈별 독자 분석의 원천) — 프로필 visit beacon 과 같은 입력.
 */
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
            // ref(쿼리) 우선 — fetch 비콘의 Referer 헤더는 비콘을 쏜 글 페이지 자신이라 유입원이
            // 못 된다. 프론트가 document.referrer 를 ref 로 실어 보내고, 헤더는 폴백.
            ref != null && !ref.isBlank() ? ref : referrer,
            userAgent,
            ClientIp.of(req),
            acceptLanguage,
            src,
            utmSource,
            utmMedium,
            utmCampaign,
            utmTerm,
            utmContent));
  }
}
