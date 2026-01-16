package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Collection search result.
 *
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemCollection extends SearchItem {

    private static final String JSON_PROPERTY_TAGS = "tags";
    private final List<String> m_tags;

    @JsonCreator
    private SearchItemCollection(@JsonProperty(SearchItem.JSON_PROPERTY_TITLE) final String title,
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
        @JsonProperty(JSON_PROPERTY_TAGS) final List<String> tags) {
        super(title, titleHighlighted, description, itemType, pathToResource, id, owner, ownerAccountId, explanation,
            matchedQueries, score, kudosCount, isPrivate);
        m_tags = tags == null ? new ArrayList<>() : tags;
    }

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        var that = (SearchItemCollection)o;
        return Objects.equals(m_tags, that.m_tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_tags, super.hashCode());
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
