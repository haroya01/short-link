package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.relay.RelayBreaker;
import com.example.short_link.federation.application.relay.RelayIntake;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.presentation.request.RelaySettingRequest;
import com.example.short_link.federation.presentation.request.RelaySubscribeRequest;
import com.example.short_link.federation.presentation.response.RelayStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/federation/relays")
@RequiredArgsConstructor
public class AdminRelayController {

  private final RelaySubscriptions subscriptions;
  private final RelayIntake intake;
  private final RelayBreaker breaker;
  private final RelayTally tally;

  @GetMapping
  public RelayStatusResponse status() {
    RelaySubscriptions.Setting setting = subscriptions.setting();
    return new RelayStatusResponse(
        subscriptions.enabled(),
        setting.mode(),
        setting.languages(),
        breaker.tripped(),
        intake.size(),
        tally.today(),
        subscriptions.list());
  }

  @PostMapping
  public RelaySubscriptions.View subscribe(@Valid @RequestBody RelaySubscribeRequest request) {
    return subscriptions.subscribe(request.actorUri());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> unsubscribe(@PathVariable Long id) {
    subscriptions.unsubscribe(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/setting")
  public RelayStatusResponse change(@Valid @RequestBody RelaySettingRequest request) {
    subscriptions.change(request.mode(), request.languages());
    breaker.rearm();
    return status();
  }
}
