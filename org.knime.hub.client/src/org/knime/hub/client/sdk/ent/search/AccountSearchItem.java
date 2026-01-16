package org.knime.hub.client.sdk.ent.search;

import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Account search suggestion result.
 *
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AccountSearchItem {

    private static final String JSON_PROPERTY_ID = "id";
    private final String m_id;

    private static final String JSON_PROPERTY_NAME = "name";
    private final String m_name;

    private static final String JSON_PROPERTY_DISPLAY_NAME = "displayName";
    private final String m_displayName;

    @JsonCreator
    private AccountSearchItem(@JsonProperty(JSON_PROPERTY_ID) final String id,
        @JsonProperty(JSON_PROPERTY_NAME) final String name,
        @JsonProperty(JSON_PROPERTY_DISPLAY_NAME) final String displayName) {
        m_id = id;
        m_name = name;
        m_displayName = displayName;
    }

    @JsonProperty(JSON_PROPERTY_ID)
    public String getId() {
        return m_id;
    }

    @JsonProperty(JSON_PROPERTY_NAME)
    public String getName() {
        return m_name;
    }

    @JsonProperty(JSON_PROPERTY_DISPLAY_NAME)
    public String getDisplayName() {
        return m_displayName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (AccountSearchItem)o;
        return Objects.equals(m_id, that.m_id)
            && Objects.equals(m_name, that.m_name)
            && Objects.equals(m_displayName, that.m_displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_id, m_name, m_displayName);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
