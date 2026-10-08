package com.example.short_link.post.application.read;

import com.example.short_link.post.application.write.MentionParser;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentMentions {

  private final UserRepository users;

  public Function<String, List<String>> in(Collection<String> bodies) {
    Set<String> handles =
        bodies.stream()
            .flatMap(body -> MentionParser.parse(body).stream())
            .collect(Collectors.toSet());
    Set<String> members =
        handles.isEmpty()
            ? Set.of()
            : users.findActiveByUsernameIn(handles).stream()
                .map(UserEntity::getUsername)
                .collect(Collectors.toSet());
    return body -> MentionParser.parse(body).stream().filter(members::contains).toList();
  }

  public List<String> of(String body) {
    return in(List.of(body)).apply(body);
  }
}
