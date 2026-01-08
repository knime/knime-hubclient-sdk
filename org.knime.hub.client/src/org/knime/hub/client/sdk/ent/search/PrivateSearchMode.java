package org.knime.hub.client.sdk.ent.search;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Private search mode flags: include/exclude/auto.
 *
 * @since 1.1.0
 */
public enum PrivateSearchMode {
    INCLUDE("include"),
    EXCLUDE("exclude"),
    AUTO("auto");

    private final String m_mode;

    PrivateSearchMode(final String mode) {
        m_mode = mode;
    }

    @JsonValue
    public String getPrivateSearchMode() {
        return m_mode;
    }

    @Override
    public String toString() {
        return m_mode;
    }

    @JsonCreator
    public static PrivateSearchMode fromValue(final String value) {
        return fromString(value).orElseThrow(
            () -> new IllegalArgumentException("Unexpected privateSearchMode '" + value + "'"));
    }

    public static Optional<PrivateSearchMode> fromString(final String value) {
        for (var mode : PrivateSearchMode.values()) {
            if (mode.m_mode.equalsIgnoreCase(value)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
