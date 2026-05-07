package com.example.short_link.admin.api;

import com.example.short_link.admin.application.AdminOverview;
import com.example.short_link.admin.application.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminOverviewService service;

  @GetMapping("/overview")
  public AdminOverview overview() {
    return service.overview();
  }
}
