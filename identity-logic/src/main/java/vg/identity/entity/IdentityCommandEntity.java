package vg.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.identity.model.IdentityCommandStatus;
import vg.identity.model.IdentityCommandType;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_command")
@Entity
@EntityListeners(AuditingEntityListener.class)
public class IdentityCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private int version;

    @Column(name = "command_status", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private IdentityCommandStatus commandStatus;

    @Column(name = "command_type", nullable = false, updatable = false)
    @Enumerated(EnumType.ORDINAL)
    private IdentityCommandType commandType;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "MEDIUMBLOB", nullable = false, updatable = false)
    private String payload;

    private Instant startedAt;

    private Instant completedAt;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(columnDefinition = "MEDIUMBLOB")
    private String errorMessage;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        var that = (IdentityCommandEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
