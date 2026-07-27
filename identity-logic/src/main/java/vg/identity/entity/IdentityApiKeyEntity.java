package vg.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "identity_api_key")
@Entity
public class IdentityApiKeyEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "principal_unique_id", nullable = false, updatable = false)
    private IdentityPrincipalEntity principal;

    @Convert(converter = StringEncryptionConverter.class)
    @Column(nullable = false, updatable = false, columnDefinition = "BLOB")
    private String label;

    @Column(name = "secret_hash", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
    private byte[] secretHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant revokedAt;
}
