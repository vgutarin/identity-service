package vg.identity.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.With;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Identity-owned encrypted metadata for an application-user membership.
 *
 * <p>Enabled flags are persisted as their enum ordinals in the compact {@code f} array. Null fields are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record IdentityApplicationUserMetadata(
        @With @JsonProperty("f") int[] flags
) {
    private static final IdentityApplicationUserMetadata EMPTY = new IdentityApplicationUserMetadata(null);

    public IdentityApplicationUserMetadata {
        flags = null == flags || 0 == flags.length ? null : IntStream.of(flags).distinct().sorted().toArray();
    }

    @Override
    public int[] flags() {
        return flags == null ? null : flags.clone();
    }

    public static IdentityApplicationUserMetadata empty() {
        return EMPTY;
    }

    public boolean hasFlag(IdentityApplicationUserMetadataFlag flag) {
        return flags != null && Arrays.stream(flags).anyMatch(ordinal -> ordinal == flag.ordinal());
    }

    public IdentityApplicationUserMetadata withFlag(IdentityApplicationUserMetadataFlag flag, boolean enabled) {

        var withEnabledFlag = IntStream.concat(null == flags ? IntStream.empty() : Arrays.stream(flags), IntStream.of(flag.ordinal()));
        if (enabled) {
            return this.withFlags(withEnabledFlag.toArray());
        }

        return this.withFlags(
                withEnabledFlag.filter(ordinal -> ordinal != flag.ordinal()).toArray()
        );
    }
}
