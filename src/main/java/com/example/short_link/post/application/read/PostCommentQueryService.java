package com.example.short_link.post.application.read;

import com.example.short_link.common.page.Keyset;
import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentLikeRepository;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentQueryService {

  private final CommentRepository commentRepository;
  private final CommentLikeRepository commentLikeRepository;
  private final UserSummaryReader userSummaries;
  private final PostRepository postRepository;
  private final CommentMentions mentions;
  private final PostAccessPolicy access;

  public PostScoped<List<CommentView>> listForPost(
      Long postId, PostViewer reader, boolean tombstones) {
    PostEntity post = postRepository.findById(postId).orElse(null);
    if (post == null || !access.readable(post, reader)) {
      return new PostScoped<>(List.of(), false);
    }
    return new PostScoped<>(
        comments(reader.viewerId(), postId, tombstones), !PostAccessPolicy.isOpen(post));
  }

  public PostScoped<CommentsView> page(
      Long postId, PostViewer reader, boolean tombstones, long cursor, int limit) {
    return readablePage(
        postId,
        reader,
        () -> {
          Keyset<CommentEntity> threads =
              Keyset.of(
                  commentRepository.heardThreads(
                      postId, reader.viewerId(), cursor, limit + 1, tombstones),
                  limit,
                  CommentEntity::getId);
          List<CommentEntity> page = new ArrayList<>(threads.rows());
          page.addAll(
              commentRepository.heardRepliesTo(
                  postId,
                  threads.rows().stream().map(CommentEntity::getId).toList(),
                  reader.viewerId(),
                  tombstones));
          page.sort(Comparator.comparing(CommentEntity::getId));
          return new Keyset<>(page, threads.next());
        });
  }

  public PostScoped<CommentsView> newer(
      Long postId, PostViewer reader, boolean tombstones, long after, int limit) {
    return readablePage(
        postId,
        reader,
        () -> {
          Keyset<CommentEntity> newer =
              Keyset.of(
                  commentRepository.heardAfter(
                      postId, reader.viewerId(), after, limit + 1, tombstones),
                  limit,
                  CommentEntity::getId);
          Set<Long> present =
              newer.rows().stream().map(CommentEntity::getId).collect(Collectors.toSet());
          Set<Long> missingParents =
              newer.rows().stream()
                  .map(CommentEntity::getParentId)
                  .filter(parent -> parent != null && !present.contains(parent))
                  .collect(Collectors.toSet());
          List<CommentEntity> withParents = new ArrayList<>(newer.rows());
          commentRepository.findAllOnPost(postId, missingParents).stream()
              .filter(parent -> tombstones || !parent.isDeleted())
              .forEach(withParents::add);
          withParents.sort(Comparator.comparing(CommentEntity::getId));
          return new Keyset<>(withParents, newer.next());
        });
  }

  private PostScoped<CommentsView> readablePage(
      Long postId, PostViewer reader, Supplier<Keyset<CommentEntity>> rows) {
    PostEntity post = postRepository.findById(postId).orElse(null);
    if (post == null || !access.readable(post, reader)) {
      return new PostScoped<>(new CommentsView(List.of(), null, "0"), false);
    }
    Keyset<CommentEntity> found = rows.get();
    return new PostScoped<>(
        new CommentsView(
            views(found.rows()), found.next(), String.valueOf(commentRepository.latestId(postId))),
        !PostAccessPolicy.isOpen(post));
  }

  private List<CommentView> comments(Long viewerId, Long postId, boolean tombstones) {
    return views(
        tombstones
            ? commentRepository.findHeardWithTombstonesByPostId(postId, viewerId)
            : commentRepository.findHeardByPostId(postId, viewerId));
  }

  private List<CommentView> views(List<CommentEntity> comments) {
    List<CommentEntity> live = comments.stream().filter(c -> !c.isDeleted()).toList();
    List<Long> authorIds = live.stream().map(CommentEntity::getUserId).distinct().toList();
    Map<Long, Long> likeCounts =
        commentLikeRepository.countByCommentIds(live.stream().map(CommentEntity::getId).toList());
    Map<Long, UserSummary> authors =
        userSummaries.findAll(authorIds).stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    Function<String, List<String>> mentioned =
        mentions.in(live.stream().map(CommentEntity::getBody).toList());

    return comments.stream()
        .map(
            c ->
                c.isDeleted()
                    ? CommentView.tombstone(c.getId(), c.getParentId(), c.getCreatedAt())
                    : new CommentView(
                        c.getId(),
                        c.getParentId(),
                        authorView(authors.get(c.getUserId())),
                        c.getBody(),
                        c.getCreatedAt(),
                        likeCounts.getOrDefault(c.getId(), 0L),
                        mentioned.apply(c.getBody())))
        .toList();
  }

  public Map<Long, List<ReplyPreview>> latest(Collection<Long> postIds, Long viewerId, int per) {
    return commentRepository.latestHeard(postIds, viewerId, per).stream()
        .collect(Collectors.groupingBy(ReplyPreview::parentId));
  }

  public List<MyCommentView> listMyComments(Long userId) {
    List<CommentEntity> comments = commentRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> postIds = comments.stream().map(CommentEntity::getPostId).distinct().toList();
    PostAccessPolicy.Lists lists = access.forLists(PostViewer.of(userId));
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(lists::keeps)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    List<Long> authorIds = posts.values().stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserSummary> authors =
        userSummaries.findAll(authorIds).stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    Map<Long, Long> likeCounts =
        commentLikeRepository.countByCommentIds(
            comments.stream().map(CommentEntity::getId).toList());

    return comments.stream()
        // 댓글의 글 참조는 FK 없는 소프트 참조라 사라진 글을 가리킬 수 있다.
        .filter(c -> posts.containsKey(c.getPostId()))
        .map(
            c -> {
              PostEntity post = posts.get(c.getPostId());
              UserSummary author = authors.get(post.getUserId());
              return new MyCommentView(
                  c.getId(),
                  c.getBody(),
                  c.getParentId(),
                  likeCounts.getOrDefault(c.getId(), 0L),
                  c.getCreatedAt(),
                  post.getSlug(),
                  post.getTitle(),
                  author == null ? null : author.username());
            })
        .toList();
  }

  public List<Long> likedCommentIds(Long userId, Long postId) {
    List<Long> ids =
        commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId).stream()
            .map(CommentEntity::getId)
            .toList();
    return commentLikeRepository.findLikedCommentIds(userId, ids);
  }

  private PublicAuthorView authorView(UserSummary user) {
    return user == null ? null : PublicAuthorView.from(user);
  }
}
