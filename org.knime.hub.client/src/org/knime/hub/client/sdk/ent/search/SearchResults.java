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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned by the KNIME Hub search-service ({@code GET /search} and
 * {@code GET /instant-search} endpoints).
 * <p>
 * Contains the ranked list of matched {@link SearchItem}s, per-category hit counts, and
 * supplementary suggestion lists (tags, usernames, team names, external groups) that are only
 * populated for instant-search requests.
 * </p>
 *
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchResults {

    private static final String JSON_PROPERTY_COUNT_BY_CATEGORY = "countByCategory";
    private final SearchResultsCountByCategory m_countByCategory;

    private static final String JSON_PROPERTY_RESULTS = "results";
    private final List<SearchItem> m_results;

    private static final String JSON_PROPERTY_SUGGESTED_TAGS = "suggestedTags";
    private final List<String> m_suggestedTags;

    private static final String JSON_PROPERTY_SUGGESTED_USERNAMES = "suggestedUsernames";
    private final List<String> m_suggestedUsernames;

    private static final String JSON_PROPERTY_SUGGESTED_TEAMNAMES = "suggestedTeamnames";
    private final List<String> m_suggestedTeamnames;

    private static final String JSON_PROPERTY_SUGGESTED_EXTERNAL_GROUPS = "suggestedExternalGroups";
    private final List<AccountSearchItem> m_suggestedExternalGroups;

    private static final String JSON_PROPERTY_RELATED_TAGS = "relatedTags";
    private final List<String> m_relatedTags;

    private static final String JSON_PROPERTY_RELATED_PATH_TAGS = "relatedPathTags";
    private final List<String> m_relatedPathTags;

    private static final String JSON_PROPERTY_TOOK = "took";
    private final Long m_took;

    private static final String JSON_PROPERTY_ES_QUERY = "esQuery";
    private final String m_esQuery;

    private static final String JSON_PROPERTY_ES_RESULT = "esResult";
    private final String m_esResult;

    /**
     * Search results returned by the KNIME Hub search service.
     *
     * @param countByCategory the hit counts broken down by item category
     * @param results the list of matched search items in ranked order
     * @param suggestedTags tags that match the search query (instant-search only)
     * @param suggestedUsernames usernames that match the search query (instant-search only)
     * @param suggestedTeamnames team names that match the search query (instant-search only)
     * @param suggestedExternalGroups external groups that match the search query (instant-search only)
     * @param relatedTags tags that appear in the tag lists of matched documents
     * @param relatedPathTags tags that appear in the path-tag lists of matched documents
     * @param took the time taken by the search engine to execute the query in milliseconds, if present
     * @param esQuery the serialized Elasticsearch query sent by the search service (debug only), if present
     * @param esResult the raw Elasticsearch response received by the search service (debug only), if present
     */
    @JsonCreator
    public SearchResults(
        @JsonProperty(value = JSON_PROPERTY_COUNT_BY_CATEGORY, required = true)
        final SearchResultsCountByCategory countByCategory,
        @JsonProperty(value = JSON_PROPERTY_RESULTS, required = true) final List<SearchItem> results,
        @JsonProperty(value = JSON_PROPERTY_SUGGESTED_TAGS) final List<String> suggestedTags,
        @JsonProperty(value = JSON_PROPERTY_SUGGESTED_USERNAMES) final List<String> suggestedUsernames,
        @JsonProperty(value = JSON_PROPERTY_SUGGESTED_TEAMNAMES) final List<String> suggestedTeamnames,
        @JsonProperty(value = JSON_PROPERTY_SUGGESTED_EXTERNAL_GROUPS)
        final List<AccountSearchItem> suggestedExternalGroups,
        @JsonProperty(value = JSON_PROPERTY_RELATED_TAGS) final List<String> relatedTags,
        @JsonProperty(value = JSON_PROPERTY_RELATED_PATH_TAGS) final List<String> relatedPathTags,
        @JsonProperty(JSON_PROPERTY_TOOK) final Long took,
        @JsonProperty(JSON_PROPERTY_ES_QUERY) final String esQuery,
        @JsonProperty(JSON_PROPERTY_ES_RESULT) final String esResult) {
        m_countByCategory = countByCategory;
        m_results = results;
        m_suggestedTags = suggestedTags == null ? List.of() : suggestedTags;
        m_suggestedUsernames = suggestedUsernames == null ? List.of() : suggestedUsernames;
        m_suggestedTeamnames = suggestedTeamnames == null ? List.of() : suggestedTeamnames;
        m_suggestedExternalGroups = suggestedExternalGroups == null ? List.of() : suggestedExternalGroups;
        m_relatedTags = relatedTags == null ? List.of() : relatedTags;
        m_relatedPathTags = relatedPathTags == null ? List.of() : relatedPathTags;
        m_took = took;
        m_esQuery = esQuery;
        m_esResult = esResult;
    }

    /**
     * Returns the hit counts broken down by item category.
     *
     * @return the counts per category
     */
    @JsonProperty(JSON_PROPERTY_COUNT_BY_CATEGORY)
    public SearchResultsCountByCategory getCountByCategory() {
        return m_countByCategory;
    }

    /**
     * Returns the list of matched search items in ranked order.
     *
     * @return the search results
     */
    @JsonProperty(JSON_PROPERTY_RESULTS)
    public List<SearchItem> getResults() {
        return m_results;
    }

    /**
     * Returns tags that match the search query (instant-search only).
     *
     * @return the suggested tags
     */
    @JsonProperty(JSON_PROPERTY_SUGGESTED_TAGS)
    public List<String> getSuggestedTags() {
        return m_suggestedTags;
    }

    /**
     * Returns usernames that match the search query (instant-search only).
     *
     * @return the suggested usernames
     */
    @JsonProperty(JSON_PROPERTY_SUGGESTED_USERNAMES)
    public List<String> getSuggestedUsernames() {
        return m_suggestedUsernames;
    }

    /**
     * Returns team names that match the search query (instant-search only).
     *
     * @return the suggested team names
     */
    @JsonProperty(JSON_PROPERTY_SUGGESTED_TEAMNAMES)
    public List<String> getSuggestedTeamnames() {
        return m_suggestedTeamnames;
    }

    /**
     * Returns external groups that match the search query (instant-search only).
     *
     * @return the suggested external groups
     */
    @JsonProperty(JSON_PROPERTY_SUGGESTED_EXTERNAL_GROUPS)
    public List<AccountSearchItem> getSuggestedExternalGroups() {
        return m_suggestedExternalGroups;
    }

    /**
     * Returns tags that appear in the tag lists of matched documents.
     *
     * @return the related tags
     */
    @JsonProperty(JSON_PROPERTY_RELATED_TAGS)
    public List<String> getRelatedTags() {
        return m_relatedTags;
    }

    /**
     * Returns tags that appear in the path-tag lists of matched documents.
     *
     * @return the related path tags
     */
    @JsonProperty(JSON_PROPERTY_RELATED_PATH_TAGS)
    public List<String> getRelatedPathTags() {
        return m_relatedPathTags;
    }

    /**
     * Returns the time taken by the search engine to execute the query in milliseconds, if present.
     *
     * @return the optional query execution time
     */
    @JsonProperty(JSON_PROPERTY_TOOK)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Long> getTook() {
        return Optional.ofNullable(m_took);
    }

    /**
     * Returns the serialized Elasticsearch query sent by the search service (debug only), if present.
     *
     * @return the optional ES query string
     */
    @JsonProperty(JSON_PROPERTY_ES_QUERY)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getEsQuery() {
        return Optional.ofNullable(m_esQuery);
    }

    /**
     * Returns the raw Elasticsearch response received by the search service (debug only), if present.
     *
     * @return the optional ES result string
     */
    @JsonProperty(JSON_PROPERTY_ES_RESULT)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getEsResult() {
        return Optional.ofNullable(m_esResult);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (SearchResults)o;
        return Objects.equals(m_countByCategory, that.m_countByCategory)
            && Objects.equals(m_results, that.m_results)
            && Objects.equals(m_suggestedTags, that.m_suggestedTags)
            && Objects.equals(m_suggestedUsernames, that.m_suggestedUsernames)
            && Objects.equals(m_suggestedTeamnames, that.m_suggestedTeamnames)
            && Objects.equals(m_suggestedExternalGroups, that.m_suggestedExternalGroups)
            && Objects.equals(m_relatedTags, that.m_relatedTags)
            && Objects.equals(m_relatedPathTags, that.m_relatedPathTags)
            && Objects.equals(m_took, that.m_took)
            && Objects.equals(m_esQuery, that.m_esQuery)
            && Objects.equals(m_esResult, that.m_esResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_countByCategory, m_results, m_suggestedTags, m_suggestedUsernames, m_suggestedTeamnames,
            m_suggestedExternalGroups, m_relatedTags, m_relatedPathTags, m_took, m_esQuery, m_esResult);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
