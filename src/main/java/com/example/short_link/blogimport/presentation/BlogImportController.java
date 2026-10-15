package com.example.short_link.blogimport.presentation;

import com.example.short_link.blogimport.application.read.ImportPreviewView;
import com.example.short_link.blogimport.application.read.ImportSourceQueryService;
import com.example.short_link.blogimport.application.read.ImportSourceView;
import com.example.short_link.blogimport.application.write.ConnectImportSourceUseCase;
import com.example.short_link.blogimport.application.write.DisconnectImportSourceUseCase;
import com.example.short_link.blogimport.application.write.PreviewBlogUseCase;
import com.example.short_link.blogimport.application.write.UpdateImportSourceUseCase;
import com.example.short_link.blogimport.application.write.VerifyImportSourceUseCase;
import com.example.short_link.blogimport.presentation.request.ImportUrlRequest;
import com.example.short_link.blogimport.presentation.request.UpdateImportSourceRequest;
import com.example.short_link.blogimport.presentation.response.ImportSourceListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class BlogImportController {

  private final PreviewBlogUseCase preview;
  private final ConnectImportSourceUseCase connect;
  private final VerifyImportSourceUseCase verify;
  private final UpdateImportSourceUseCase update;
  private final DisconnectImportSourceUseCase disconnect;
  private final ImportSourceQueryService sources;

  @PostMapping("/preview")
  public ImportPreviewView preview(@Valid @RequestBody ImportUrlRequest request) {
    return preview.execute(request.url());
  }

  @PostMapping("/sources")
  @ResponseStatus(HttpStatus.CREATED)
  public ImportSourceView connect(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ImportUrlRequest request) {
    return connect.execute(userId, request.url());
  }

  @PostMapping("/sources/{id}/verify")
  public ImportSourceView verify(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return verify.execute(userId, id);
  }

  @GetMapping("/sources")
  public ImportSourceListResponse list(@AuthenticationPrincipal Long userId) {
    return new ImportSourceListResponse(sources.list(userId));
  }

  @GetMapping("/sources/{id}")
  public ImportSourceView get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return sources.get(userId, id);
  }

  @PatchMapping("/sources/{id}")
  public ImportSourceView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody UpdateImportSourceRequest request) {
    return update.execute(userId, id, request.announceNewPosts(), request.showInDiscover());
  }

  @DeleteMapping("/sources/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "true") boolean keepPosts) {
    disconnect.execute(userId, id, keepPosts);
  }
}
