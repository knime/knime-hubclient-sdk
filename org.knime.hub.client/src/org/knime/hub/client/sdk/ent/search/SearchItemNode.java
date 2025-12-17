package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemNode extends SearchItem {

    private static final String JSON_PROPERTY_ICON = "icon";
    private static final String JSON_PROPERTY_FEATURE_SYMBOLIC_NAME = "featureSymbolicName";
    private static final String JSON_PROPERTY_TAGS = "tags";
    private static final String JSON_PROPERTY_KEYWORDS = "keywords";

    private Icon m_icon;
    private String m_featureSymbolicName;
    private List<String> m_tags = new ArrayList<>();
    private List<String> m_keywords = new ArrayList<>();

    @JsonProperty(JSON_PROPERTY_ICON)
    public Icon getIcon() {
        return m_icon;
    }

    public void setIcon(final Icon icon) {
        m_icon = icon;
    }

    @JsonProperty(JSON_PROPERTY_FEATURE_SYMBOLIC_NAME)
    public String getFeatureSymbolicName() {
        return m_featureSymbolicName;
    }

    public void setFeatureSymbolicName(final String featureSymbolicName) {
        m_featureSymbolicName = featureSymbolicName;
    }

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    public void setTags(final List<String> tags) {
        m_tags = tags;
    }

    @JsonProperty(JSON_PROPERTY_KEYWORDS)
    public List<String> getKeywords() {
        return m_keywords;
    }

    public void setKeywords(final List<String> keywords) {
        m_keywords = keywords;
    }
}
