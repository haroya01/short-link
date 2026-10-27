package archfixtures.modules.alpha.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AlphaEntity {
  @Id private Long id;

  public Long getId() {
    return id;
  }
}
