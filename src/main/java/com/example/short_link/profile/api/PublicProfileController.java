package com.example.short_link.profile.api;

import com.example.short_link.profile.application.ProfileNotFoundException;
import com.example.short_link.profile.application.ProfileService;
import com.example.short_link.profile.application.PublicProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicProfileController {

  private final ProfileService service;

  @GetMapping("/{username}")
  public PublicProfile get(@PathVariable String username) {
    return service.findByUsername(username);
  }

  @ExceptionHandler(ProfileNotFoundException.class)
  public ResponseEntity<String> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("not found");
  }
}
