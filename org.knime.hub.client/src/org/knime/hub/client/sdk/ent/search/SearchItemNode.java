package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Node search result.
 *
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemNode extends SearchItem {

    private static final String JSON_PROPERTY_ICON = "icon";
    private final Icon m_icon;

    private static final String JSON_PROPERTY_FEATURE_SYMBOLIC_NAME = "featureSymbolicName";
    private final String m_featureSymbolicName;

    private static final String JSON_PROPERTY_TAGS = "tags";
    private final List<String> m_tags;

    private static final String JSON_PROPERTY_KEYWORDS = "keywords";
    private final List<String> m_keywords;

    @JsonCreator
    private SearchItemNode(@JsonProperty(SearchItem.JSON_PROPERTY_TITLE) final String title,
        @JsonProperty(SearchItem.JSON_PROPERTY_TITLE_HIGHLIGHTED) final String titleHighlighted,
        @JsonProperty(SearchItem.JSON_PROPERTY_DESCRIPTION) final String description,
        @JsonProperty(SearchItem.JSON_PROPERTY_ITEM_TYPE) final SearchItemType itemType,
        @JsonProperty(SearchItem.JSON_PROPERTY_PATH) final String pathToResource,
        @JsonProperty(SearchItem.JSON_PROPERTY_ID) final String id,
        @JsonProperty(SearchItem.JSON_PROPERTY_OWNER) final String owner,
        @JsonProperty(SearchItem.JSON_PROPERTY_OWNER_ACCOUNT_ID) final String ownerAccountId,
        @JsonProperty(SearchItem.JSON_PROPERTY_EXPLANATION) final String explanation,
        @JsonProperty(SearchItem.JSON_PROPERTY_MATCHED_QUERIES) final String[] matchedQueries,
        @JsonProperty(SearchItem.JSON_PROPERTY_SCORE) final Float score,
        @JsonProperty(SearchItem.JSON_PROPERTY_KUDOS) final Integer kudosCount,
        @JsonProperty(SearchItem.JSON_PROPERTY_PRIVATE) final Boolean isPrivate,
        @JsonProperty(JSON_PROPERTY_ICON) final Icon icon,
        @JsonProperty(JSON_PROPERTY_FEATURE_SYMBOLIC_NAME) final String featureSymbolicName,
        @JsonProperty(JSON_PROPERTY_TAGS) final List<String> tags,
        @JsonProperty(JSON_PROPERTY_KEYWORDS) final List<String> keywords) {
        super(title, titleHighlighted, description, itemType, pathToResource, id, owner, ownerAccountId, explanation,
            matchedQueries, score, kudosCount, isPrivate);
        m_icon = icon;
        m_featureSymbolicName = featureSymbolicName;
        m_tags = tags == null ? new ArrayList<>() : tags;
        m_keywords = keywords == null ? new ArrayList<>() : keywords;
    }

    @JsonProperty(JSON_PROPERTY_ICON)
    public Icon getIcon() {
        return m_icon;
    }

    @JsonProperty(JSON_PROPERTY_FEATURE_SYMBOLIC_NAME)
    public String getFeatureSymbolicName() {
        return m_featureSymbolicName;
    }

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    @JsonProperty(JSON_PROPERTY_KEYWORDS)
    public List<String> getKeywords() {
        return m_keywords;
    }
}
