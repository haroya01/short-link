package com.example.short_link.link.presentation;

import com.example.short_link.link.application.WeeklyInsightsService;
import com.example.short_link.link.application.dto.WeeklyInsights;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/insights")
@RequiredArgsConstructor
public class WeeklyInsightsController {

  private final WeeklyInsightsService service;

  @GetMapping("/week")
  public WeeklyInsights week(@AuthenticationPrincipal Long userId) {
    return service.compute(userId);
  }
}
