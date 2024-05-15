package vg.identity.entity;


import jakarta.persistence.Column;
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
public class UserEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    @Column(unique = true, nullable = false)
    private String username;
    private String password;

    private Instant createdAtTime;

}
