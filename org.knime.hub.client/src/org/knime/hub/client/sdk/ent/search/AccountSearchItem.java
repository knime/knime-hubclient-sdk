package org.knime.hub.client.sdk.ent.search;

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
}
