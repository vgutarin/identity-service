package vg.identity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class IdentityRoleTemplate {
    private Long id;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
    private String name;
    private String description;
    @Builder.Default
    private Set<String> permissions = new HashSet<>();
}
