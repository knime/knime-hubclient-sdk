package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Collection search result.
 *
 * @since 1.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemCollection extends SearchItem {

    private static final String JSON_PROPERTY_TAGS = "tags";

    private List<String> m_tags = new ArrayList<>();

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    public void setTags(final List<String> tags) {
        m_tags = tags;
    }
}
