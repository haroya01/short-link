package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.directory.DirectorySources;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.federation.presentation.request.DirectorySourceRequest;
import com.example.short_link.federation.presentation.response.DirectoryStatusResponse;
import com.example.short_link.federation.scheduler.DirectoryWorker;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/admin/federation/directories")
@RequiredArgsConstructor
public class AdminDirectoryController {

  private final DirectorySources sources;
  private final DirectoryWorker worker;

  @GetMapping
  public DirectoryStatusResponse status() {
    return new DirectoryStatusResponse(sources.enabled(), worker.running(), sources.list());
  }

  @PutMapping("/{host:.+}")
  public DirectorySources.View put(
      @PathVariable String host, @Valid @RequestBody DirectorySourceRequest request) {
    return sources.put(host, request.enabled(), request.cap());
  }

  @DeleteMapping("/{host:.+}")
  public ResponseEntity<Void> delete(@PathVariable String host) {
    sources.delete(host);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/run")
  public ResponseEntity<Map<String, Boolean>> run() {
    if (!sources.enabled()) {
      throw new FederationException(FederationErrorCode.DIRECTORY_DISABLED);
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("started", worker.trigger()));
  }
}
