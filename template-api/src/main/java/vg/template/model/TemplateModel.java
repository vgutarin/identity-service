package vg.template.model;

import lombok.Builder;
import lombok.Data;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/**
 * Represents some event
 */
@Data
@Builder
public class TemplateModel implements Identifiable {

    private UniqueId uniqueId;
    private String name;
    private String description;

    private Instant createdAtTime;
    private int version;
}
