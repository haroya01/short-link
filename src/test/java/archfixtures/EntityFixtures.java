package archfixtures;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Lives outside the application package so persistence never maps these. */
public final class EntityFixtures {

  private EntityFixtures() {}

  @Entity
  public static class EntityWithPublicSetter {
    @Id private Long id;
    private String note;

    public void setNote(String note) {
      this.note = note;
    }
  }

  @Entity
  public static class EntityWithNamedOperation {
    @Id private Long id;
    private String note;

    public void rename(String note) {
      this.note = note;
    }
  }
}
