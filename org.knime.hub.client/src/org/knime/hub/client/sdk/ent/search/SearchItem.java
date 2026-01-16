package org.knime.hub.client.sdk.ent.search;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Base class for search hits returned by the Hub search-service.
 *
 * @since 1.1
 */
@JsonIgnoreProperties(value = SearchItem.JSON_PROPERTY_ITEM_TYPE, allowSetters = true, ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = SearchItem.JSON_PROPERTY_ITEM_TYPE, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SearchItemWorkflow.class, name = SearchItem.SearchItemType.Values.WORKFLOW),
    @JsonSubTypes.Type(value = SearchItemNode.class, name = SearchItem.SearchItemType.Values.NODE),
    @JsonSubTypes.Type(value = SearchItemExtension.class, name = SearchItem.SearchItemType.Values.EXTENSION),
    @JsonSubTypes.Type(value = SearchItemComponent.class, name = SearchItem.SearchItemType.Values.COMPONENT),
    @JsonSubTypes.Type(value = SearchItemCollection.class, name = SearchItem.SearchItemType.Values.COLLECTION)
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class SearchItem {

    protected static final String JSON_PROPERTY_TITLE = "title";
    private final String m_title;

    protected static final String JSON_PROPERTY_TITLE_HIGHLIGHTED = "titleHighlighted";
    private final String m_titleHighlighted;

    protected static final String JSON_PROPERTY_DESCRIPTION = "description";
    private final String m_description;

    protected static final String JSON_PROPERTY_ITEM_TYPE = "itemType";
    private final SearchItemType m_itemType;

    protected static final String JSON_PROPERTY_PATH = "pathToResource";
    private final String m_pathToResource;

    protected static final String JSON_PROPERTY_ID = "id";
    private final String m_id;

    protected static final String JSON_PROPERTY_OWNER = "owner";
    private final String m_owner;

    protected static final String JSON_PROPERTY_OWNER_ACCOUNT_ID = "ownerAccountId";
    private final String m_ownerAccountId;

    protected static final String JSON_PROPERTY_EXPLANATION = "explanation";
    private final String m_explanation;

    protected static final String JSON_PROPERTY_MATCHED_QUERIES = "matchedQueries";
    private final String[] m_matchedQueries;

    protected static final String JSON_PROPERTY_SCORE = "score";
    private final Float m_score;

    protected static final String JSON_PROPERTY_KUDOS = "kudosCount";
    private final Integer m_kudosCount;

    protected static final String JSON_PROPERTY_PRIVATE = "private";
    private final Boolean m_private;

    @JsonCreator
    protected SearchItem(@JsonProperty(JSON_PROPERTY_TITLE) final String title,
        @JsonProperty(JSON_PROPERTY_TITLE_HIGHLIGHTED) final String titleHighlighted,
        @JsonProperty(JSON_PROPERTY_DESCRIPTION) final String description,
        @JsonProperty(JSON_PROPERTY_ITEM_TYPE) final SearchItemType itemType,
        @JsonProperty(JSON_PROPERTY_PATH) final String pathToResource,
        @JsonProperty(JSON_PROPERTY_ID) final String id,
        @JsonProperty(JSON_PROPERTY_OWNER) final String owner,
        @JsonProperty(JSON_PROPERTY_OWNER_ACCOUNT_ID) final String ownerAccountId,
        @JsonProperty(JSON_PROPERTY_EXPLANATION) final String explanation,
        @JsonProperty(JSON_PROPERTY_MATCHED_QUERIES) final String[] matchedQueries,
        @JsonProperty(JSON_PROPERTY_SCORE) final Float score,
        @JsonProperty(JSON_PROPERTY_KUDOS) final Integer kudosCount,
        @JsonProperty(JSON_PROPERTY_PRIVATE) final Boolean isPrivate) {
        m_title = title;
        m_titleHighlighted = titleHighlighted;
        m_description = description;
        m_itemType = itemType;
        m_pathToResource = pathToResource;
        m_id = id;
        m_owner = owner;
        m_ownerAccountId = ownerAccountId;
        m_explanation = explanation;
        m_matchedQueries = matchedQueries;
        m_score = score;
        m_kudosCount = kudosCount;
        m_private = isPrivate;
    }

    @JsonProperty(JSON_PROPERTY_TITLE)
    public String getTitle() {
        return m_title;
    }

    @JsonProperty(JSON_PROPERTY_TITLE_HIGHLIGHTED)
    public String getTitleHighlighted() {
        return m_titleHighlighted;
    }

    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    public String getDescription() {
        return m_description;
    }

    /**
     * Returns the item type.
     *
     * @return type
     */
    @JsonProperty(JSON_PROPERTY_ITEM_TYPE)
    public SearchItemType getItemType() {
        return m_itemType;
    }

    @JsonProperty(JSON_PROPERTY_PATH)
    public String getPathToResource() {
        return m_pathToResource;
    }

    @JsonProperty(JSON_PROPERTY_ID)
    public String getId() {
        return m_id;
    }

    @JsonProperty(JSON_PROPERTY_OWNER)
    public String getOwner() {
        return m_owner;
    }

    @JsonProperty(JSON_PROPERTY_OWNER_ACCOUNT_ID)
    public String getOwnerAccountId() {
        return m_ownerAccountId;
    }

    @JsonProperty(JSON_PROPERTY_EXPLANATION)
    public String getExplanation() {
        return m_explanation;
    }

    @JsonProperty(JSON_PROPERTY_MATCHED_QUERIES)
    public Optional<String[]> getMatchedQueries() {
        return Optional.ofNullable(m_matchedQueries).map(qs -> Arrays.copyOf(qs, qs.length));
    }

    @JsonProperty(JSON_PROPERTY_SCORE)
    public Float getScore() {
        return m_score;
    }

    @JsonProperty(JSON_PROPERTY_KUDOS)
    public Integer getKudosCount() {
        return m_kudosCount;
    }

    @JsonProperty(JSON_PROPERTY_PRIVATE)
    public Boolean isPrivate() {
        return m_private;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (SearchItem)o;
        return Objects.equals(m_title, that.m_title)
            && Objects.equals(m_titleHighlighted, that.m_titleHighlighted)
            && Objects.equals(m_description, that.m_description)
            && Objects.equals(m_itemType, that.m_itemType)
            && Objects.equals(m_pathToResource, that.m_pathToResource)
            && Objects.equals(m_id, that.m_id)
            && Objects.equals(m_owner, that.m_owner)
            && Objects.equals(m_ownerAccountId, that.m_ownerAccountId)
            && Objects.equals(m_explanation, that.m_explanation)
            && Arrays.equals(m_matchedQueries, that.m_matchedQueries)
            && Objects.equals(m_score, that.m_score)
            && Objects.equals(m_kudosCount, that.m_kudosCount)
            && Objects.equals(m_private, that.m_private);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_title, m_titleHighlighted, m_description, m_itemType, m_pathToResource, m_id, m_owner,
            m_ownerAccountId, m_explanation, Arrays.hashCode(m_matchedQueries), m_score, m_kudosCount, m_private);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }

    /**
     * Item types supported by the search-service.
     */
    public enum SearchItemType {
        WORKFLOW(Values.WORKFLOW),
        NODE(Values.NODE),
        EXTENSION(Values.EXTENSION),
        COMPONENT(Values.COMPONENT),
        COLLECTION(Values.COLLECTION),
        UNKNOWN(Values.UNKNOWN);

        static final class Values {
            static final String WORKFLOW = "Workflow";
            static final String NODE = "Node";
            static final String EXTENSION = "Extension";
            static final String COMPONENT = "Component";
            static final String COLLECTION = "Collection";
            static final String UNKNOWN = "Unknown";

            private Values() {
            }
        }

        private final String m_value;

        SearchItemType(final String value) {
            m_value = value;
        }

        @JsonValue
        public String getValue() {
            return m_value;
        }

        @Override
        public String toString() {
            return m_value;
        }

        @JsonCreator
        public static SearchItemType fromValue(final String value) {
            for (var v : SearchItemType.values()) {
                if (Objects.equals(v.m_value, value)) {
                    return v;
                }
            }
            return SearchItemType.UNKNOWN;
        }
    }
}
