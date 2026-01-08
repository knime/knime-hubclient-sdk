package org.knime.hub.client.sdk.ent.search;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

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
 * @since 1.1.0
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
    protected static final String JSON_PROPERTY_TITLE_HIGHLIGHTED = "titleHighlighted";
    protected static final String JSON_PROPERTY_DESCRIPTION = "description";
    protected static final String JSON_PROPERTY_ITEM_TYPE = "itemType";
    protected static final String JSON_PROPERTY_PATH = "pathToResource";
    protected static final String JSON_PROPERTY_ID = "id";
    protected static final String JSON_PROPERTY_OWNER = "owner";
    protected static final String JSON_PROPERTY_OWNER_ACCOUNT_ID = "ownerAccountId";
    protected static final String JSON_PROPERTY_EXPLANATION = "explanation";
    protected static final String JSON_PROPERTY_MATCHED_QUERIES = "matchedQueries";
    protected static final String JSON_PROPERTY_SCORE = "score";
    protected static final String JSON_PROPERTY_KUDOS = "kudosCount";
    protected static final String JSON_PROPERTY_PRIVATE = "private";

    private String m_title;
    private String m_titleHighlighted;
    private String m_description;
    private SearchItemType m_itemType;
    private String m_pathToResource;
    private String m_id;
    private String m_owner;
    private String m_ownerAccountId;
    private String m_explanation;
    private String[] m_matchedQueries;
    private Float m_score;
    private Integer m_kudosCount;
    private Boolean m_private;

    /**
     * Returns the item type.
     *
     * @return type
     */
    @JsonProperty(JSON_PROPERTY_ITEM_TYPE)
    public SearchItemType getItemType() {
        return m_itemType;
    }

    /**
     * Sets the item type.
     *
     * @param itemType type
     */
    @JsonProperty(JSON_PROPERTY_ITEM_TYPE)
    public void setItemType(final SearchItemType itemType) {
        m_itemType = itemType;
    }

    @JsonProperty(JSON_PROPERTY_TITLE)
    public String getTitle() {
        return m_title;
    }

    public void setTitle(final String title) {
        m_title = title;
    }

    @JsonProperty(JSON_PROPERTY_TITLE_HIGHLIGHTED)
    public String getTitleHighlighted() {
        return m_titleHighlighted;
    }

    public void setTitleHighlighted(final String titleHighlighted) {
        m_titleHighlighted = titleHighlighted;
    }

    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    public String getDescription() {
        return m_description;
    }

    public void setDescription(final String description) {
        m_description = description;
    }

    @JsonProperty(JSON_PROPERTY_PATH)
    public String getPathToResource() {
        return m_pathToResource;
    }

    public void setPathToResource(final String pathToResource) {
        m_pathToResource = pathToResource;
    }

    @JsonProperty(JSON_PROPERTY_ID)
    public String getId() {
        return m_id;
    }

    public void setId(final String id) {
        m_id = id;
    }

    @JsonProperty(JSON_PROPERTY_OWNER)
    public String getOwner() {
        return m_owner;
    }

    public void setOwner(final String owner) {
        m_owner = owner;
    }

    @JsonProperty(JSON_PROPERTY_OWNER_ACCOUNT_ID)
    public String getOwnerAccountId() {
        return m_ownerAccountId;
    }

    public void setOwnerAccountId(final String ownerAccountId) {
        m_ownerAccountId = ownerAccountId;
    }

    @JsonProperty(JSON_PROPERTY_EXPLANATION)
    public String getExplanation() {
        return m_explanation;
    }

    public void setExplanation(final String explanation) {
        m_explanation = explanation;
    }

    @JsonProperty(JSON_PROPERTY_MATCHED_QUERIES)
    public Optional<String[]> getMatchedQueries() {
        return Optional.ofNullable(m_matchedQueries).map(qs -> Arrays.copyOf(qs, qs.length));
    }

    public void setMatchedQueries(final String[] matchedQueries) {
        m_matchedQueries = matchedQueries;
    }

    @JsonProperty(JSON_PROPERTY_SCORE)
    public Float getScore() {
        return m_score;
    }

    public void setScore(final Float score) {
        m_score = score;
    }

    @JsonProperty(JSON_PROPERTY_KUDOS)
    public Integer getKudosCount() {
        return m_kudosCount;
    }

    public void setKudosCount(final Integer kudosCount) {
        m_kudosCount = kudosCount;
    }

    @JsonProperty(JSON_PROPERTY_PRIVATE)
    public Boolean isPrivate() {
        return m_private;
    }

    public void setPrivate(final Boolean isPrivate) {
        m_private = isPrivate;
    }

    /**
     * Item types supported by the search-service.
     */
    public enum SearchItemType {
        WORKFLOW(Values.WORKFLOW),
        NODE(Values.NODE),
        EXTENSION(Values.EXTENSION),
        COMPONENT(Values.COMPONENT),
        COLLECTION(Values.COLLECTION);

        static final class Values {
            static final String WORKFLOW = "Workflow";
            static final String NODE = "Node";
            static final String EXTENSION = "Extension";
            static final String COMPONENT = "Component";
            static final String COLLECTION = "Collection";

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
            throw new IllegalArgumentException("Unexpected itemType '" + value + "'");
        }
    }
}
