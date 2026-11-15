package vg.identity.model;

/** Persisted ordinals and public names are stable; append new values without reordering or renaming. */
public enum IdentityMessageStatus {
    QUEUED, SENDING, SENT, FAILED, UNKNOWN
}
