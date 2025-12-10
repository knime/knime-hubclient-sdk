/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 */
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
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.1
 */
@JsonIgnoreProperties(value = SearchItem.JSON_PROPERTY_ITEM_TYPE, allowSetters = true, ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = SearchItem.JSON_PROPERTY_ITEM_TYPE, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SearchItemWorkflow.class, name = SearchItemWorkflow.TYPE),
    @JsonSubTypes.Type(value = SearchItemNode.class, name = SearchItemNode.TYPE),
    @JsonSubTypes.Type(value = SearchItemExtension.class, name = SearchItemExtension.TYPE),
    @JsonSubTypes.Type(value = SearchItemComponent.class, name = SearchItemComponent.TYPE),
    @JsonSubTypes.Type(value = SearchItemCollection.class, name = SearchItemCollection.TYPE)
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings({"java:S1694", "MissingJavadoc"}) // S1694: abstract base for JSON polymorphism, not a utility class
public abstract sealed class SearchItem
    permits SearchItemWorkflow, SearchItemNode, SearchItemExtension, SearchItemComponent, SearchItemCollection {

    protected static final String JSON_PROPERTY_ITEM_TYPE = "itemType";

    /**
     * Item types supported by the search-service.
     */
    public enum SearchItemType {

        WORKFLOW(SearchItemWorkflow.TYPE),
        NODE(SearchItemNode.TYPE),
        EXTENSION(SearchItemExtension.TYPE),
        COMPONENT(SearchItemComponent.TYPE),
        COLLECTION(SearchItemCollection.TYPE);

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
        private static SearchItemType fromValue(final String value) {
            for (SearchItemType b : values()) {
                if (b.m_value.equals(value)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }
    }

    protected static final String JSON_PROPERTY_TITLE = "title";
    private final String m_title;

    protected static final String JSON_PROPERTY_TITLE_HIGHLIGHTED = "titleHighlighted";
    private final String m_titleHighlighted;

    protected static final String JSON_PROPERTY_DESCRIPTION = "description";
    private final String m_description;

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
        @JsonProperty(value = JSON_PROPERTY_PATH, required = true) final String pathToResource,
        @JsonProperty(value = JSON_PROPERTY_ID, required = true) final String id,
        @JsonProperty(value = JSON_PROPERTY_OWNER, required = true) final String owner,
        @JsonProperty(JSON_PROPERTY_OWNER_ACCOUNT_ID) final String ownerAccountId,
        @JsonProperty(JSON_PROPERTY_EXPLANATION) final String explanation,
        @JsonProperty(JSON_PROPERTY_MATCHED_QUERIES) final String[] matchedQueries,
        @JsonProperty(JSON_PROPERTY_SCORE) final Float score,
        @JsonProperty(JSON_PROPERTY_KUDOS) final Integer kudosCount,
        @JsonProperty(value = JSON_PROPERTY_PRIVATE, required = true) final Boolean isPrivate) {
        m_title = title;
        m_titleHighlighted = titleHighlighted;
        m_description = description;
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
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getTitle() {
        return Optional.ofNullable(m_title);
    }

    @JsonProperty(JSON_PROPERTY_TITLE_HIGHLIGHTED)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getTitleHighlighted() {
        return Optional.ofNullable(m_titleHighlighted);
    }

    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getDescription() {
        return Optional.ofNullable(m_description);
    }

    /**
     * Returns the item type.
     *
     * @return type
     */
    @JsonProperty(JSON_PROPERTY_ITEM_TYPE)
    public abstract SearchItemType getItemType();

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
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getOwnerAccountId() {
        return Optional.ofNullable(m_ownerAccountId);
    }

    @JsonProperty(JSON_PROPERTY_EXPLANATION)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getExplanation() {
        return Optional.ofNullable(m_explanation);
    }

    @JsonProperty(JSON_PROPERTY_MATCHED_QUERIES)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String[]> getMatchedQueries() {
        return Optional.ofNullable(m_matchedQueries).map(qs -> Arrays.copyOf(qs, qs.length));
    }

    @JsonProperty(JSON_PROPERTY_SCORE)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Float> getScore() {
        return Optional.ofNullable(m_score);
    }

    @JsonProperty(JSON_PROPERTY_KUDOS)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Integer> getKudosCount() {
        return Optional.ofNullable(m_kudosCount);
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
        return Objects.hash(m_title, m_titleHighlighted, m_description, m_pathToResource, m_id, m_owner,
            m_ownerAccountId, m_explanation, Arrays.hashCode(m_matchedQueries), m_score, m_kudosCount, m_private);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
