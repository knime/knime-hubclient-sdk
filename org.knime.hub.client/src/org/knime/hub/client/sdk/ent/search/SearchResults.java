package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchResults {

    private static final String JSON_PROPERTY_COUNT_BY_CATEGORY = "countByCategory";
    private static final String JSON_PROPERTY_RESULTS = "results";
    private static final String JSON_PROPERTY_SUGGESTED_TAGS = "suggestedTags";
    private static final String JSON_PROPERTY_SUGGESTED_USERNAMES = "suggestedUsernames";
    private static final String JSON_PROPERTY_SUGGESTED_TEAMNAMES = "suggestedTeamnames";
    private static final String JSON_PROPERTY_SUGGESTED_EXTERNAL_GROUPS = "suggestedExternalGroups";
    private static final String JSON_PROPERTY_RELATED_TAGS = "relatedTags";
    private static final String JSON_PROPERTY_RELATED_PATH_TAGS = "relatedPathTags";
    private static final String JSON_PROPERTY_TOOK = "took";
    private static final String JSON_PROPERTY_ES_QUERY = "esQuery";
    private static final String JSON_PROPERTY_ES_RESULT = "esResult";

    private SearchResultsCountByCategory m_countByCategory;
    private List<SearchItem> m_results = new ArrayList<>();
    private List<String> m_suggestedTags = new ArrayList<>();
    private List<String> m_suggestedUsernames = new ArrayList<>();
    private List<String> m_suggestedTeamnames = new ArrayList<>();
    private List<AccountSearchItem> m_suggestedExternalGroups = new ArrayList<>();
    private List<String> m_relatedTags = new ArrayList<>();
    private List<String> m_relatedPathTags = new ArrayList<>();
    private Long m_took;
    private String m_esQuery;
    private String m_esResult;

    @JsonProperty(JSON_PROPERTY_COUNT_BY_CATEGORY)
    public SearchResultsCountByCategory getCountByCategory() {
        return m_countByCategory;
    }

    public void setCountByCategory(final SearchResultsCountByCategory countByCategory) {
        m_countByCategory = countByCategory;
    }

    @JsonProperty(JSON_PROPERTY_RESULTS)
    public List<SearchItem> getResults() {
        return m_results;
    }

    public void setResults(final List<SearchItem> results) {
        m_results = results;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_TAGS)
    public List<String> getSuggestedTags() {
        return m_suggestedTags;
    }

    public void setSuggestedTags(final List<String> suggestedTags) {
        m_suggestedTags = suggestedTags;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_USERNAMES)
    public List<String> getSuggestedUsernames() {
        return m_suggestedUsernames;
    }

    public void setSuggestedUsernames(final List<String> suggestedUsernames) {
        m_suggestedUsernames = suggestedUsernames;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_TEAMNAMES)
    public List<String> getSuggestedTeamnames() {
        return m_suggestedTeamnames;
    }

    public void setSuggestedTeamnames(final List<String> suggestedTeamnames) {
        m_suggestedTeamnames = suggestedTeamnames;
    }

    @JsonProperty(JSON_PROPERTY_SUGGESTED_EXTERNAL_GROUPS)
    public List<AccountSearchItem> getSuggestedExternalGroups() {
        return m_suggestedExternalGroups;
    }

    public void setSuggestedExternalGroups(final List<AccountSearchItem> suggestedExternalGroups) {
        m_suggestedExternalGroups = suggestedExternalGroups;
    }

    @JsonProperty(JSON_PROPERTY_RELATED_TAGS)
    public List<String> getRelatedTags() {
        return m_relatedTags;
    }

    public void setRelatedTags(final List<String> relatedTags) {
        m_relatedTags = relatedTags;
    }

    @JsonProperty(JSON_PROPERTY_RELATED_PATH_TAGS)
    public List<String> getRelatedPathTags() {
        return m_relatedPathTags;
    }

    public void setRelatedPathTags(final List<String> relatedPathTags) {
        m_relatedPathTags = relatedPathTags;
    }

    @JsonProperty(JSON_PROPERTY_TOOK)
    public Long getTook() {
        return m_took;
    }

    public void setTook(final Long took) {
        m_took = took;
    }

    @JsonProperty(JSON_PROPERTY_ES_QUERY)
    public Optional<String> getEsQuery() {
        return Optional.ofNullable(m_esQuery);
    }

    public void setEsQuery(final String esQuery) {
        m_esQuery = esQuery;
    }

    @JsonProperty(JSON_PROPERTY_ES_RESULT)
    public Optional<String> getEsResult() {
        return Optional.ofNullable(m_esResult);
    }

    public void setEsResult(final String esResult) {
        m_esResult = esResult;
    }
}
