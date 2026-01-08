/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 * -------------------------------------------------------------------
 */

package org.knime.hub.client.sdk.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.knime.core.node.util.CheckUtils;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.HTTPQueryParameter;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.ent.search.PrivateSearchMode;
import org.knime.hub.client.sdk.ent.search.SearchResults;
import org.knime.hub.client.sdk.ent.search.SearchResultsCountByCategory;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

/**
 * Search service client for KNIME Hub search-service.
 *
 * @since 1.1.0
 */
public final class SearchServiceClient {

    private static final String SEARCH_API_PATH = "search";

    private static final String QUERY_PARAM_QUERY = "query";
    private static final String QUERY_PARAM_TYPE = "type";
    private static final String QUERY_PARAM_LIMIT = "limit";
    private static final String QUERY_PARAM_OFFSET = "offset";
    private static final String QUERY_PARAM_SORT = "sort";
    private static final String QUERY_PARAM_PRIVATE_SEARCH_MODE = "privateSearchMode";
    private static final String QUERY_PARAM_DEBUG = "debug";
    private static final String QUERY_PARAM_SCORE_LIMIT = "scoreLimit";
    private static final String QUERY_PARAM_TAG = "tag";
    private static final String QUERY_PARAM_OWNER = "owner";

    private static final GenericType<SearchResults> SEARCH_RESULTS = new GenericType<>() {};
    private static final GenericType<SearchResultsCountByCategory> SEARCH_RESULTS_COUNT = new GenericType<>() {};

    private final @NotOwning ApiClient m_apiClient;

    /**
     * Create the {@link SearchServiceClient} given an {@link ApiClient}.
     *
     * @param apiClient the {@link ApiClient}
     */
    public SearchServiceClient(final @NotOwning ApiClient apiClient) {
        m_apiClient = apiClient;
    }

    /**
     * Executes a search request against the Hub search-service.
     *
     * @param query search text (empty string matches all)
     * @param type result type filter, {@code null} to use service default
     * @param limit number of results to return, {@code null} to use service default
     * @param offset first result offset, {@code null} to use service default
     * @param sort sort mode, {@code null} to use service default
     * @param privateSearchMode include/exclude/auto private items
     * @param tags optional list of tags (comma separated in query)
     * @param owner optional owner filter
     * @param debug enable debug output
     * @param scoreLimit custom score limit (debug/internal)
     * @param additionalHeaders additional headers to forward
     * @return {@link ApiResponse} containing {@link SearchResults}
     * @throws HubFailureIOException if the request fails
     */
    public ApiResponse<SearchResults> search(final String query, final SearchType type, final Integer limit,
        final Integer offset, final SearchSort sort, final PrivateSearchMode privateSearchMode,
        final List<String> tags, final String owner, final Boolean debug, final Integer scoreLimit,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {

        final var requestPath = IPath.forPosix(SEARCH_API_PATH);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_QUERY, query) //
            .withQueryParam(QUERY_PARAM_TYPE, Optional.ofNullable(type).map(SearchType::getValue).orElse(null)) //
            .withQueryParam(QUERY_PARAM_LIMIT, toString(limit)) //
            .withQueryParam(QUERY_PARAM_OFFSET, toString(offset)) //
            .withQueryParam(QUERY_PARAM_SORT, Optional.ofNullable(sort).map(SearchSort::getValue).orElse(null)) //
            .withQueryParam(QUERY_PARAM_PRIVATE_SEARCH_MODE,
                Optional.ofNullable(privateSearchMode).map(PrivateSearchMode::getPrivateSearchMode).orElse(null)) //
            .withQueryParam(QUERY_PARAM_DEBUG, toString(debug)) //
            .withQueryParam(QUERY_PARAM_SCORE_LIMIT, toString(scoreLimit)) //
            .withQueryParam(tagsToQueryParameter(tags).orElse(null)) //
            .withQueryParam(QUERY_PARAM_OWNER, owner) //
            .invokeAPI(requestPath, ApiClient.Method.GET, null, SEARCH_RESULTS);
    }


    private static String toString(final Number number) {
        return number == null ? null : number.toString();
    }

    private static String toString(final Boolean bool) {
        return bool == null ? null : Boolean.toString(bool);
    }

    /**
     * Encodes tags as a comma-separated query parameter. The search-service accepts repeated parameters, but the
     * current ApiClient stores query parameters in a map; to avoid silently dropping tags, we join them.
     */
    private static Optional<HTTPQueryParameter> tagsToQueryParameter(final List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Optional.empty();
        }
        final var value = tags.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
            .collect(Collectors.joining(","));
        return value.isEmpty() ? Optional.empty() : Optional.of(new HTTPQueryParameter(QUERY_PARAM_TAG, value));
    }

    /**
     * Search type filter.
     */
    public enum SearchType {
        WORKFLOW("workflow"),
        COMPONENT("component"),
        NODE("node"),
        EXTENSION("extension"),
        COLLECTION("collection"),
        ALL("all");

        private final String m_value;

        SearchType(final String value) {
            m_value = value;
        }

        public String getValue() {
            return m_value;
        }
    }

    /**
     * Sort modes supported by the search-service.
     */
    public enum SearchSort {
        NEW("new"),
        OLD("old"),
        BEST("best"),
        MAX_DOWNLOADS("maxDownloads"),
        MIN_DOWNLOADS("minDownloads"),
        MAX_KUDOS("maxKudos"),
        MIN_KUDOS("minKudos");

        private final String m_value;

        SearchSort(final String value) {
            m_value = value;
        }

        public String getValue() {
            return m_value;
        }
    }
}
