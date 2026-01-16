package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Search response payload.
 *
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

    @JsonCreator
    private SearchResults(@JsonProperty(JSON_PROPERTY_COUNT_BY_CATEGORY) final SearchResultsCountByCategory countByCategory,
        @JsonProperty(JSON_PROPERTY_RESULTS) final List<SearchItem> results,
        @JsonProperty(JSON_PROPERTY_SUGGESTED_TAGS) final List<String> suggestedTags,
        @JsonProperty(JSON_PROPERTY_SUGGESTED_USERNAMES) final List<String> suggestedUsernames,
        @JsonProperty(JSON_PROPERTY_SUGGESTED_TEAMNAMES) final List<String> suggestedTeamnames,
        @JsonProperty(JSON_PROPERTY_SUGGESTED_EXTERNAL_GROUPS) final List<AccountSearchItem> suggestedExternalGroups,
        @JsonProperty(JSON_PROPERTY_RELATED_TAGS) final List<String> relatedTags,
        @JsonProperty(JSON_PROPERTY_RELATED_PATH_TAGS) final List<String> relatedPathTags,
        @JsonProperty(JSON_PROPERTY_TOOK) final Long took,
        @JsonProperty(JSON_PROPERTY_ES_QUERY) final String esQuery,
        @JsonProperty(JSON_PROPERTY_ES_RESULT) final String esResult) {
        m_countByCategory = countByCategory;
        m_results = results == null ? new ArrayList<>() : results;
        m_suggestedTags = suggestedTags == null ? new ArrayList<>() : suggestedTags;
        m_suggestedUsernames = suggestedUsernames == null ? new ArrayList<>() : suggestedUsernames;
        m_suggestedTeamnames = suggestedTeamnames == null ? new ArrayList<>() : suggestedTeamnames;
        m_suggestedExternalGroups = suggestedExternalGroups == null ? new ArrayList<>() : suggestedExternalGroups;
        m_relatedTags = relatedTags == null ? new ArrayList<>() : relatedTags;
        m_relatedPathTags = relatedPathTags == null ? new ArrayList<>() : relatedPathTags;
        m_took = took;
        m_esQuery = esQuery;
        m_esResult = esResult;
    }

    @JsonProperty(JSON_PROPERTY_COUNT_BY_CATEGORY)
    public SearchResultsCountByCategory getCountByCategory() {
        return m_countByCategory;
    }

    @JsonProperty(JSON_PROPERTY_RESULTS)
    public List<SearchItem> getResults() {
        return m_results;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_TAGS)
    public List<String> getSuggestedTags() {
        return m_suggestedTags;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_USERNAMES)
    public List<String> getSuggestedUsernames() {
        return m_suggestedUsernames;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_TEAMNAMES)
    public List<String> getSuggestedTeamnames() {
        return m_suggestedTeamnames;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_EXTERNAL_GROUPS)
    public List<AccountSearchItem> getSuggestedExternalGroups() {
        return m_suggestedExternalGroups;
    }

    @JsonProperty(JSON_PROPERTY_RELATED_TAGS)
    public List<String> getRelatedTags() {
        return m_relatedTags;
    }

    @JsonProperty(JSON_PROPERTY_RELATED_PATH_TAGS)
    public List<String> getRelatedPathTags() {
        return m_relatedPathTags;
    }

    @JsonProperty(JSON_PROPERTY_TOOK)
    public Long getTook() {
        return m_took;
    }

    @JsonProperty(JSON_PROPERTY_ES_QUERY)
    public Optional<String> getEsQuery() {
        return Optional.ofNullable(m_esQuery);
    }

    @JsonProperty(JSON_PROPERTY_ES_RESULT)
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
