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

import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.ent.search.ComponentSuggestions;
import org.knime.hub.client.sdk.ent.search.IdentitySuggestions;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

/**
 * Client for the KNIME Hub search-service suggestions endpoints.
 *
 * @since 1.4
 */
public final class SuggestionsServiceClient {

    private static final String COMPONENT_SUGGESTIONS_API_PATH = "suggestions/components";
    private static final String IDENTITY_SUGGESTIONS_API_PATH = "suggestions/identities";

    private static final String QUERY_PARAM_QUERY = "query";
    private static final String QUERY_PARAM_LIMIT = "limit";
    private static final String QUERY_PARAM_IN_PORT = "inPort";
    private static final String QUERY_PARAM_OUT_PORT = "outPort";

    private static final GenericType<ComponentSuggestions> COMPONENT_SUGGESTIONS = new GenericType<>() {};
    private static final GenericType<IdentitySuggestions> IDENTITY_SUGGESTIONS = new GenericType<>() {};

    private final @NotOwning ApiClient m_apiClient;

    /**
     * Create the {@link SuggestionsServiceClient} given an {@link ApiClient}.
     *
     * @param apiClient the {@link ApiClient}
     */
    public SuggestionsServiceClient(final @NotOwning ApiClient apiClient) {
        m_apiClient = apiClient;
    }

    /**
     * Executes a component suggestions request against the Hub search-service.
     *
     * @param query search text
     * @param limit number of suggestions to return, {@code null} to use service default
     * @param inPort object class of required input port, mutually exclusive with {@code outPort}
     * @param outPort object class of required output port, mutually exclusive with {@code inPort}
     * @param additionalHeaders additional headers to forward
     * @return {@link ApiResponse} containing {@link ComponentSuggestions}
     * @throws IllegalArgumentException if both {@code inPort} and {@code outPort} are non-empty
     * @throws HubFailureIOException if the request fails
     */
    public ApiResponse<ComponentSuggestions> suggestComponents(final String query, final Integer limit,
        final String inPort, final String outPort, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {

        if (isNonEmpty(inPort) && isNonEmpty(outPort)) {
            throw new IllegalArgumentException("'inPort' and 'outPort' are mutually exclusive");
        }

        final var requestPath = IPath.forPosix(COMPONENT_SUGGESTIONS_API_PATH);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_QUERY, query) //
            .withQueryParam(QUERY_PARAM_LIMIT, toString(limit)) //
            .withQueryParam(QUERY_PARAM_IN_PORT, inPort) //
            .withQueryParam(QUERY_PARAM_OUT_PORT, outPort) //
            .invokeAPI(requestPath, ApiClient.Method.GET, null, COMPONENT_SUGGESTIONS);
    }

    /**
     * Executes an identity suggestions request against the Hub search-service.
     *
     * @param query search text
     * @param limit number of suggestions to return, {@code null} to use service default
     * @param additionalHeaders additional headers to forward
     * @return {@link ApiResponse} containing {@link IdentitySuggestions}
     * @throws HubFailureIOException if the request fails
     */
    public ApiResponse<IdentitySuggestions> suggestIdentities(final String query, final Integer limit,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {

        final var requestPath = IPath.forPosix(IDENTITY_SUGGESTIONS_API_PATH);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_QUERY, query) //
            .withQueryParam(QUERY_PARAM_LIMIT, toString(limit)) //
            .invokeAPI(requestPath, ApiClient.Method.GET, null, IDENTITY_SUGGESTIONS);
    }

    private static String toString(final Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isNonEmpty(final String value) {
        return value != null && !value.isEmpty();
    }
}
