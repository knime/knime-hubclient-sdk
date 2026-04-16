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
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.HTTPQueryParameter;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.ent.search.SearchResults;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

/**
 * Client for the KNIME Hub search-service {@code GET /search} endpoint.
 * <p>
 * Supports filtering by item type, owner, and tags; pagination via limit/offset; and multiple sort
 * modes. The response is deserialized into a {@link SearchResults} object containing ranked
 * {@link org.knime.hub.client.sdk.ent.search.SearchItem} instances.
 * </p>
 *
 * @since 1.1
 */
public final class SearchServiceClient {

    private static final String SEARCH_API_PATH = "search";

    private static final String QUERY_PARAM_QUERY = "query";
    private static final String QUERY_PARAM_TYPE = "type";
    private static final String QUERY_PARAM_LIMIT = "limit";
    private static final String QUERY_PARAM_OFFSET = "offset";
    private static final String QUERY_PARAM_SORT = "sort";
    private static final String QUERY_PARAM_SEARCH_MODE = "searchMode";
    private static final String QUERY_PARAM_DEBUG = "debug";
    private static final String QUERY_PARAM_SCORE_LIMIT = "scoreLimit";
    private static final String QUERY_PARAM_TAG = "tag";
    private static final String QUERY_PARAM_OWNER = "owner";

    private static final GenericType<SearchResults> SEARCH_RESULTS = new GenericType<>() {};

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
     * @param searchMode search scope: {@link SearchMode#GLOBAL} for public/global results,
     *            {@link SearchMode#SCOPED} for results scoped to caller visibility (recommended replacement for
     *            legacy {@code privateSearchMode=include})
     * @param tags optional list of tags (comma separated in query)
     * @param owner optional owner filter
     * @param debug enable debug output
     * @param scoreLimit custom score limit (debug/internal)
     * @param additionalHeaders additional headers to forward
     * @return {@link ApiResponse} containing {@link SearchResults}
     * @throws HubFailureIOException if the request fails
     */
    @SuppressWarnings("java:S107") // S107: API signature mirrors search-service parameters
    public ApiResponse<SearchResults> search(final String query, final SearchType type, final Integer limit,
        final Integer offset, final SearchSort sort, final SearchMode searchMode,
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
            .withQueryParam(QUERY_PARAM_SEARCH_MODE,
                Optional.ofNullable(searchMode).map(SearchMode::getValue).orElse(null)) //
            .withQueryParam(QUERY_PARAM_DEBUG, toString(debug)) //
            .withQueryParam(QUERY_PARAM_SCORE_LIMIT, toString(scoreLimit)) //
            .withQueryParam(tagsToQueryParameter(tags).orElse(null)) //
            .withQueryParam(QUERY_PARAM_OWNER, owner) //
            .invokeAPI(requestPath, ApiClient.Method.GET, null, SEARCH_RESULTS);
    }

    /**
     * @deprecated Use {@link #search(String, SearchType, Integer, Integer, SearchSort, SearchMode, List, String,
     *             Boolean, Integer, Map)} with {@link SearchMode}. The upstream search-service removed
     *             {@code privateSearchMode} in favor of {@code searchMode}.
     */
    @Deprecated(since = "1.3", forRemoval = false)
    @SuppressWarnings("java:S107") // S107: API signature mirrors search-service parameters
    public ApiResponse<SearchResults> search(final String query, final SearchType type, final Integer limit,
        final Integer offset, final SearchSort sort, final PrivateSearchMode privateSearchMode,
        final List<String> tags, final String owner, final Boolean debug, final Integer scoreLimit,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        return search(query, type, limit, offset, sort, toSearchMode(privateSearchMode), tags, owner, debug, scoreLimit,
            additionalHeaders);
    }


    private static String toString(final Object value) {
        return value == null ? null : value.toString();
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
        /** Filter to workflow items only. */
        WORKFLOW("workflow"),
        /** Filter to component items only. */
        COMPONENT("component"),
        /** Filter to node items only. */
        NODE("node"),
        /** Filter to extension items only. */
        EXTENSION("extension"),
        /** Filter to collection items only. */
        COLLECTION("collection"),
        /** No type filter – return all item types. */
        ALL("all");

        private final String m_value;

        SearchType(final String value) {
            m_value = value;
        }

        /**
         * Returns the lower-case string value sent as the {@code type} query parameter.
         *
         * @return the API value
         */
        public String getValue() {
            return m_value;
        }
    }

    /**
     * Sort modes supported by the search-service.
     */
    public enum SearchSort {
        /** Sort by newest first (creation date descending). */
        NEW("new"),
        /** Sort by oldest first (creation date ascending). */
        OLD("old"),
        /** Sort by relevance score (default). */
        BEST("best"),
        /** Sort by download count descending. */
        MAX_DOWNLOADS("maxDownloads"),
        /** Sort by download count ascending. */
        MIN_DOWNLOADS("minDownloads"),
        /** Sort by kudos count descending. */
        MAX_KUDOS("maxKudos"),
        /** Sort by kudos count ascending. */
        MIN_KUDOS("minKudos");

        private final String m_value;

        SearchSort(final String value) {
            m_value = value;
        }

        /**
         * Returns the string value sent as the {@code sort} query parameter.
         *
         * @return the API value
         */
        public String getValue() {
            return m_value;
        }
    }

    /**
     * Search scope flags.
     */
    public enum SearchMode {
        /**
         * Global/public search scope.
         */
        GLOBAL("global"),
        /**
         * Scoped search based on caller visibility (e.g. includes private-accessible items).
         */
        SCOPED("scoped");

        private final String m_value;

        SearchMode(final String value) {
            m_value = value;
        }

        /**
         * Returns the string value sent as the {@code searchMode} query parameter.
         *
         * @return the API value
         */
        public String getValue() {
            return m_value;
        }
    }

    /**
     * Legacy private-search mode flags.
     *
     * @deprecated Use {@link SearchMode}. The upstream search-service no longer supports
     *             {@code privateSearchMode}.
     */
    @Deprecated(since = "1.3", forRemoval = false)
    public enum PrivateSearchMode {
        INCLUDE,
        EXCLUDE,
        AUTO;
    }

    private static SearchMode toSearchMode(final PrivateSearchMode privateSearchMode) {
        if (privateSearchMode == null) {
            return null;
        }
        return switch (privateSearchMode) {
            case INCLUDE -> SearchMode.SCOPED;
            case EXCLUDE, AUTO -> SearchMode.GLOBAL;
        };
    }
}
