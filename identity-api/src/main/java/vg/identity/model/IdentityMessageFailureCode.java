package vg.identity.model;

/**
 * Safe delivery outcomes; provider messages and credentials are never exposed.
 * Persisted ordinals and public names are stable; append new values without reordering or renaming.
 */
public enum IdentityMessageFailureCode {
    DELIVERY_UNAVAILABLE, PROVIDER_REJECTED, INTERNAL_ERROR, DELIVERY_UNKNOWN
}
