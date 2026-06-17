package vg.identity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class IdentityWorkspace implements Identifiable {
    private UniqueId uniqueId;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
    private String name;
}
