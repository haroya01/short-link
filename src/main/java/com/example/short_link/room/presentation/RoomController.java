package com.example.short_link.room.presentation;

import com.example.short_link.room.application.read.RoomInviteView;
import com.example.short_link.room.application.read.RoomListItem;
import com.example.short_link.room.application.read.RoomQueryService;
import com.example.short_link.room.application.read.RoomSuggestions;
import com.example.short_link.room.application.read.RoomView;
import com.example.short_link.room.application.write.RoomCommandService;
import com.example.short_link.room.application.write.RoomInvitationService;
import com.example.short_link.room.application.write.RoomInviteService;
import com.example.short_link.room.domain.RoomInvitation;
import com.example.short_link.room.domain.RoomInvitee;
import com.example.short_link.room.domain.RoomJoinRequest;
import com.example.short_link.room.presentation.request.CreateRoomInviteRequest;
import com.example.short_link.room.presentation.request.CreateRoomRequest;
import com.example.short_link.room.presentation.request.DismissRoomSuggestionsRequest;
import com.example.short_link.room.presentation.request.InviteToRoomRequest;
import com.example.short_link.room.presentation.request.LeaveRoomRequest;
import com.example.short_link.room.presentation.request.ReadRoomRequest;
import com.example.short_link.room.presentation.request.UpdateRoomRequest;
import com.example.short_link.room.presentation.response.FeedHiddenResponse;
import com.example.short_link.room.presentation.response.JoinRoomResponse;
import com.example.short_link.room.presentation.response.UnreadResponse;
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
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

  private final RoomQueryService query;
  private final RoomCommandService command;
  private final RoomInviteService invites;
  private final RoomInvitationService invitations;

  @GetMapping
  public List<RoomListItem> mine(@AuthenticationPrincipal Long userId) {
    return query.mine(userId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoomView create(
      @AuthenticationPrincipal Long userId, @RequestBody CreateRoomRequest request) {
    return command.create(userId, request.name());
  }

  @GetMapping("/suggestions")
  public RoomSuggestions suggestions(@AuthenticationPrincipal Long userId) {
    return query.suggestions(userId);
  }

  @PostMapping("/suggestions/dismiss")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void dismiss(
      @AuthenticationPrincipal Long userId, @RequestBody DismissRoomSuggestionsRequest request) {
    command.dismissSuggestions(userId, request.userIds());
  }

  @GetMapping("/invitations")
  public List<RoomInvitation> invitations(@AuthenticationPrincipal Long userId) {
    return query.invitations(userId);
  }

  @PostMapping("/invitations/{roomId}/accept")
  public JoinRoomResponse accept(@AuthenticationPrincipal Long userId, @PathVariable Long roomId) {
    return JoinRoomResponse.of(invitations.accept(userId, roomId));
  }

  @PostMapping("/invitations/{roomId}/decline")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void decline(@AuthenticationPrincipal Long userId, @PathVariable Long roomId) {
    invitations.decline(userId, roomId);
  }

  @GetMapping("/{id}")
  public RoomView detail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return query.detail(id, userId);
  }

  @PatchMapping("/{id}")
  public RoomView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody UpdateRoomRequest request) {
    return command.update(userId, id, request.name(), request.approvalRequired());
  }

  @PostMapping("/{id}/read")
  public UnreadResponse read(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody ReadRoomRequest request) {
    return new UnreadResponse(command.read(userId, id, request.noteId()));
  }

  @PutMapping("/{id}/feed-hidden")
  public FeedHiddenResponse hide(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return new FeedHiddenResponse(command.hideFromFeed(userId, id, true));
  }

  @DeleteMapping("/{id}/feed-hidden")
  public FeedHiddenResponse show(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return new FeedHiddenResponse(command.hideFromFeed(userId, id, false));
  }

  @PostMapping("/{id}/leave")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void leave(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody(required = false) LeaveRoomRequest request) {
    command.leave(userId, id, request != null && Boolean.TRUE.equals(request.deleteMyNotes()));
  }

  @DeleteMapping("/{id}/members/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(
      @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long memberId) {
    command.remove(userId, id, memberId);
  }

  @GetMapping("/{id}/invites")
  public List<RoomInviteView> invites(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return query.invites(id, userId);
  }

  @PostMapping("/{id}/invites")
  @ResponseStatus(HttpStatus.CREATED)
  public RoomInviteView invite(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody(required = false) CreateRoomInviteRequest request) {
    CreateRoomInviteRequest options =
        request == null ? new CreateRoomInviteRequest(null, null, null) : request;
    return invites.create(
        userId, id, options.maxUses(), options.expiresInSeconds(), options.approval());
  }

  @DeleteMapping("/{id}/invites/{inviteId}")
  public RoomInviteView disable(
      @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long inviteId) {
    return invites.disable(userId, id, inviteId);
  }

  @GetMapping("/{id}/invitations")
  public List<RoomInvitee> invitees(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return query.invitees(id, userId);
  }

  @PostMapping("/{id}/invitations")
  public List<RoomInvitee> inviteByName(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody InviteToRoomRequest request) {
    return invitations.invite(userId, id, request.userIds());
  }

  @DeleteMapping("/{id}/invitations/{inviteeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelInvitation(
      @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long inviteeId) {
    invitations.cancel(userId, id, inviteeId);
  }

  @GetMapping("/{id}/requests")
  public List<RoomJoinRequest> requests(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return query.requests(id, userId);
  }

  @PostMapping("/{id}/requests/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void approve(
      @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long memberId) {
    invites.approve(userId, id, memberId);
  }

  @DeleteMapping("/{id}/requests/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reject(
      @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long memberId) {
    invites.reject(userId, id, memberId);
  }
}
