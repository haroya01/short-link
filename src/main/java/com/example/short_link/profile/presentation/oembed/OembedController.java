package com.example.short_link.profile.presentation.oembed;

import com.example.short_link.profile.application.oembed.OembedMetadata;
import com.example.short_link.profile.application.oembed.OembedService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.URL;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/oembed")
@RequiredArgsConstructor
@Validated
public class OembedController {

  private final OembedService service;

  @GetMapping
  public OembedMetadata oembed(@RequestParam("url") @NotBlank @URL @Size(max = 2048) String url) {
    return service.fetch(url);
  }
}
