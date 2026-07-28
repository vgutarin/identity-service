package vg.identity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class IdentityRole {
    private UniqueId uniqueId;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
    private String name;
    private String description;
    private Long workspaceUniqueId;
    @Builder.Default
    private Set<String> permissions = new HashSet<>();
}
