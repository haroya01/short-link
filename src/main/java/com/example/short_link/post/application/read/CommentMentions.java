package com.example.short_link.post.application.read;

import com.example.short_link.post.application.write.MentionParser;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
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

  private final UserSummaryReader users;

  public Function<String, List<String>> in(Collection<String> bodies) {
    Set<String> handles =
        bodies.stream()
            .flatMap(body -> MentionParser.parse(body).stream())
            .collect(Collectors.toSet());
    Set<String> members =
        handles.isEmpty()
            ? Set.of()
            : users.findActiveByUsernames(handles).stream()
                .map(UserSummary::username)
                .collect(Collectors.toSet());
    return body -> MentionParser.parse(body).stream().filter(members::contains).toList();
  }

  public List<String> of(String body) {
    return in(List.of(body)).apply(body);
  }
}
