package com.example.short_link.event.presentation;

import com.example.short_link.event.application.image.EventCoverImageService;
import com.example.short_link.event.application.image.EventCoverImageService.PresignResult;
import com.example.short_link.event.application.read.AttendeeView;
import com.example.short_link.event.application.read.EventAnalyticsQueryService;
import com.example.short_link.event.application.read.EventAnalyticsView;
import com.example.short_link.event.application.read.EventQueryService;
import com.example.short_link.event.application.read.EventView;
import com.example.short_link.event.application.write.ChangeEventStatusUseCase;
import com.example.short_link.event.application.write.CreateEventAliasLinkUseCase;
import com.example.short_link.event.application.write.CreateEventCommand;
import com.example.short_link.event.application.write.CreateEventUseCase;
import com.example.short_link.event.application.write.EventLinkIssuer.IssuedLink;
import com.example.short_link.event.application.write.UpdateEventCommand;
import com.example.short_link.event.application.write.UpdateEventUseCase;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.presentation.request.ChangeStatusRequest;
import com.example.short_link.event.presentation.request.CommitCoverRequest;
import com.example.short_link.event.presentation.request.CreateAliasLinkRequest;
import com.example.short_link.event.presentation.request.CreateEventRequest;
import com.example.short_link.event.presentation.request.PresignCoverRequest;
import com.example.short_link.event.presentation.request.QuestionSpecRequest;
import com.example.short_link.event.presentation.request.UpdateEventRequest;
import com.example.short_link.event.presentation.response.CommitCoverResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

  private final CreateEventUseCase createEvent;
  private final UpdateEventUseCase updateEvent;
  private final ChangeEventStatusUseCase changeStatus;
  private final CreateEventAliasLinkUseCase createAliasLink;
  private final EventQueryService eventQueryService;
  private final EventAnalyticsQueryService analyticsQueryService;
  private final EventCoverImageService coverImageService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EventView create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateEventRequest request) {
    EventEntity created =
        createEvent.execute(
            new CreateEventCommand(
                userId,
                request.title(),
                request.descriptionMd(),
                request.startsAt(),
                request.endsAt(),
                request.timezone(),
                request.locationText(),
                request.locationUrl(),
                request.onlineUrl(),
                request.capacity(),
                request.closeAt(),
                parseContactField(request.contactField()),
                specsOf(request.questions())));
    return eventQueryService.findOwnEvent(userId, created.getId());
  }

  @GetMapping
  public List<EventView> listMine(@AuthenticationPrincipal Long userId) {
    return eventQueryService.listMyEvents(userId);
  }

  @GetMapping("/{id}")
  public EventView find(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return eventQueryService.findOwnEvent(userId, id);
  }

  @PatchMapping("/{id}")
  public EventView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody UpdateEventRequest request) {
    updateEvent.execute(
        new UpdateEventCommand(
            userId,
            id,
            request.title(),
            request.descriptionMd(),
            request.startsAt(),
            request.endsAt(),
            request.timezone(),
            request.locationText(),
            request.locationUrl(),
            request.onlineUrl(),
            request.capacity(),
            request.closeAt(),
            specsOf(request.questions())));
    return eventQueryService.findOwnEvent(userId, id);
  }

  @PostMapping("/{id}/status")
  public EventView changeStatus(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody ChangeStatusRequest request) {
    changeStatus.execute(
        userId, id, ChangeEventStatusUseCase.Action.valueOf(request.action().toUpperCase()));
    return eventQueryService.findOwnEvent(userId, id);
  }

  @PostMapping("/{id}/links")
  @ResponseStatus(HttpStatus.CREATED)
  public IssuedLink createLink(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody CreateAliasLinkRequest request) {
    return createAliasLink.execute(userId, id, request.label());
  }

  @GetMapping("/{id}/attendees")
  public List<AttendeeView> attendees(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return eventQueryService.listAttendees(userId, id);
  }

  @GetMapping("/{id}/attendees.csv")
  public ResponseEntity<byte[]> attendeesCsv(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    byte[] body = eventQueryService.exportAttendeesCsv(userId, id).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendees.csv\"")
        .body(body);
  }

  @GetMapping("/{id}/analytics")
  public EventAnalyticsView analytics(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return analyticsQueryService.analyze(userId, id);
  }

  @PostMapping("/{id}/cover/presign")
  public PresignResult presignCover(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody PresignCoverRequest request) {
    return coverImageService.presignUpload(userId, id, request.contentType());
  }

  @PostMapping("/{id}/cover/commit")
  public CommitCoverResponse commitCover(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody CommitCoverRequest request) {
    return new CommitCoverResponse(coverImageService.commitUpload(userId, id, request.key()));
  }

  private static ContactField parseContactField(String raw) {
    return raw == null || raw.isBlank()
        ? ContactField.EMAIL
        : ContactField.valueOf(raw.toUpperCase());
  }

  private static List<com.example.short_link.event.application.helper.EventQuestions.QuestionSpec>
      specsOf(List<QuestionSpecRequest> requests) {
    return requests == null ? null : requests.stream().map(QuestionSpecRequest::toSpec).toList();
  }
}
