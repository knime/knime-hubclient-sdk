package org.knime.hub.client.sdk.ent.search;

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
    private static final String JSON_PROPERTY_NAME = "name";
    private static final String JSON_PROPERTY_DISPLAY_NAME = "displayName";

    private String m_id;
    private String m_name;
    private String m_displayName;

    @JsonProperty(JSON_PROPERTY_ID)
    public String getId() {
        return m_id;
    }

    public void setId(final String id) {
        m_id = id;
    }

    @JsonProperty(JSON_PROPERTY_NAME)
    public String getName() {
        return m_name;
    }

    public void setName(final String name) {
        m_name = name;
    }

    @JsonProperty(JSON_PROPERTY_DISPLAY_NAME)
    public String getDisplayName() {
        return m_displayName;
    }

    public void setDisplayName(final String displayName) {
        m_displayName = displayName;
    }
}
