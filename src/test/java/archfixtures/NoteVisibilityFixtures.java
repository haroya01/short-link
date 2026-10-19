package archfixtures;

import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.note.application.access.NoteAccess;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.Set;

public final class NoteVisibilityFixtures {

  private NoteVisibilityFixtures() {}

  public static class ShowsANoteBecauseItIsPublic {
    boolean shown(NoteEntity note) {
      return note.getVisibility() == NoteVisibility.PUBLIC;
    }
  }

  public static class SwitchesOverTheVisibility {
    boolean shown(NoteVisibility visibility) {
      return switch (visibility) {
        case PUBLIC, UNLISTED -> true;
        default -> false;
      };
    }
  }

  public static class AsksTheVisibility {
    boolean shown(NoteEntity note) {
      return note.getVisibility().shareable();
    }
  }

  public static class LooksUpReadersItself {
    Set<Long> readable(NoteRepository notes, Long viewerId, Set<Long> ids) {
      return notes.visibleTo(viewerId, ids);
    }
  }

  public static class AsksNoteAccess {
    boolean readable(NoteAccess access, NoteEntity note, Long viewerId) {
      return access.readable(note, viewerId);
    }
  }
}
