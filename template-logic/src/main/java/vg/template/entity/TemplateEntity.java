package vg.template.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.jpa.UniqueIdEntity;

import java.time.Instant;


@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
public class TemplateEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    private String name;
    private String description;

    private Instant createdAtTime;

}
