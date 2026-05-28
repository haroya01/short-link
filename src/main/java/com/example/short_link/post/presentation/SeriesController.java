package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.SeriesDetailView;
import com.example.short_link.post.application.read.SeriesQueryService;
import com.example.short_link.post.application.read.SeriesView;
import com.example.short_link.post.application.write.CreateSeriesCommand;
import com.example.short_link.post.application.write.CreateSeriesUseCase;
import com.example.short_link.post.application.write.DeleteSeriesCommand;
import com.example.short_link.post.application.write.DeleteSeriesUseCase;
import com.example.short_link.post.application.write.SetSeriesPostsCommand;
import com.example.short_link.post.application.write.SetSeriesPostsUseCase;
import com.example.short_link.post.application.write.UpdateSeriesCommand;
import com.example.short_link.post.application.write.UpdateSeriesUseCase;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.presentation.request.CreateSeriesRequest;
import com.example.short_link.post.presentation.request.SetSeriesPostsRequest;
import com.example.short_link.post.presentation.request.UpdateSeriesRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/series")
@RequiredArgsConstructor
public class SeriesController {

  private final CreateSeriesUseCase createSeries;
  private final UpdateSeriesUseCase updateSeries;
  private final DeleteSeriesUseCase deleteSeries;
  private final SetSeriesPostsUseCase setSeriesPosts;
  private final SeriesQueryService seriesQueryService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SeriesDetailView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateSeriesRequest request) {
    SeriesEntity created =
        createSeries.execute(new CreateSeriesCommand(userId, request.slug(), request.title()));
    return seriesQueryService.getMine(userId, created.getId());
  }

  @GetMapping
  public List<SeriesView> listMine(@AuthenticationPrincipal Long userId) {
    return seriesQueryService.listMine(userId);
  }

  @GetMapping("/{id}")
  public SeriesDetailView get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return seriesQueryService.getMine(userId, id);
  }

  @PatchMapping("/{id}")
  public SeriesDetailView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody UpdateSeriesRequest request) {
    updateSeries.execute(new UpdateSeriesCommand(userId, id, request.title(), request.slug()));
    return seriesQueryService.getMine(userId, id);
  }

  @PutMapping("/{id}/posts")
  public SeriesDetailView setPosts(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody SetSeriesPostsRequest request) {
    setSeriesPosts.execute(new SetSeriesPostsCommand(userId, id, request.postIds()));
    return seriesQueryService.getMine(userId, id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deleteSeries.execute(new DeleteSeriesCommand(userId, id));
  }
}
