package org.knime.hub.client.sdk.ent.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Extension search result.
 *
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemExtension extends SearchItem {

    private static final String JSON_PROPERTY_VENDOR = "vendor";
    private final String m_vendor;

    private static final String JSON_PROPERTY_TRUSTED = "trusted";
    private final Boolean m_trusted;

    @JsonCreator
    private SearchItemExtension(@JsonProperty(SearchItem.JSON_PROPERTY_TITLE) final String title,
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
        @JsonProperty(JSON_PROPERTY_VENDOR) final String vendor,
        @JsonProperty(JSON_PROPERTY_TRUSTED) final Boolean trusted) {
        super(title, titleHighlighted, description, itemType, pathToResource, id, owner, ownerAccountId, explanation,
            matchedQueries, score, kudosCount, isPrivate);
        m_vendor = vendor;
        m_trusted = trusted;
    }

    @JsonProperty(JSON_PROPERTY_VENDOR)
    public String getVendor() {
        return m_vendor;
    }

    @JsonProperty(JSON_PROPERTY_TRUSTED)
    public Boolean isTrusted() {
        return m_trusted;
    }
}
