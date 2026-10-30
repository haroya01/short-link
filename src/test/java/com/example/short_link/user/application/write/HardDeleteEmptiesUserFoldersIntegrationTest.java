package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.support.InMemoryObjectStorage;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest
@ActiveProfiles("test")
class HardDeleteEmptiesUserFoldersIntegrationTest {

  @TestBean private ObjectStorage storage;
  @Autowired private UserDeletionService deletion;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;

  static ObjectStorage storage() {
    return new InMemoryObjectStorage();
  }

  @BeforeEach
  void aUser() {
    String name = "fld" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    userId = users.save(user).getId();
  }

  @AfterEach
  void deleteWhatIsLeft() {
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void aHardDeleteLeavesNothingInAnyFolderTheUserUploadedInto() {
    List<String> theirs =
        List.of(
            "avatars/" + userId + "/a.png",
            "banners/" + userId + "/b.png",
            "profile-images/" + userId + "/c.png",
            "post-images/" + userId + "/7/d.png",
            "post-images/" + userId + "/7/d.w480.webp",
            "event-covers/" + userId + "/3/e.png",
            "note-images/" + userId + "/f.png");
    List<String> neighbours =
        List.of(
            "avatars/" + userId + "0/a.png",
            "post-images/" + (userId + 1) + "/7/d.png",
            "note-images/" + (userId + 1) + "/f.png");
    Stream.concat(theirs.stream(), neighbours.stream())
        .forEach(key -> storage.putObject(key, "image/png", new byte[] {1}));

    deletion.hardDelete(userId);

    assertThat(((InMemoryObjectStorage) storage).keys())
        .containsExactlyInAnyOrderElementsOf(neighbours);
  }
}
