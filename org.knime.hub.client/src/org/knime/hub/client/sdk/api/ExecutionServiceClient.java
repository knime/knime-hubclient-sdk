/*
 * ------------------------------------------------------------------------
 *
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
 * ---------------------------------------------------------------------
 *
 * History
 *   Jun 27, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.api;

import java.util.Map;
import java.util.Optional;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiClient.Method;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.HTTPQueryParameter;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.ent.execution.Deployment;
import org.knime.hub.client.sdk.ent.execution.DeploymentCreationBody;
import org.knime.hub.client.sdk.ent.execution.DeploymentList;
import org.knime.hub.client.sdk.ent.execution.ExecutionContextList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

/**
 * Execution service client for KNIME Hub.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@SuppressWarnings("java:S107") // Number of parameters per endpoint is not controllable
public final class ExecutionServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionServiceClient.class);

    /* API paths */
    private static final String EXECUTION_CONTEXTS_API_PATH = "execution-contexts";
    private static final String DEPLOYMENTS_API_PATH = "deployments";

    /* Path pieces */
    private static final String PATH_PIECE_SCHEDULES = "schedules";
    private static final String PATH_PIECE_DATA_APPS = "data-apps";
    private static final String PATH_PIECE_REST = "rest";
    private static final String PATH_PIECE_TRIGGERS = "triggers";

    /* Query parameters */
    private static final String QUERY_PARAM_WORKFLOW_URI = "workflowUri";
    private static final String QUERY_PARAM_ITEM_VERSION = "itemVersion";
    private static final String QUERY_PARAM_Q = "q";
    private static final String QUERY_PARAM_SORT = "sort";
    private static final String QUERY_PARAM_PAGE = "page";
    private static final String QUERY_PARAM_PAGELEN = "pagelen";

    /* Item version query parameter "special" values */
    private static final String ITEM_VERSION_MOST_RECENT_IDENTIFIER = "most-recent";
    private static final String ITEM_VERSION_CURRENT_STATE_IDENTIFIER = "current-state";

    /* Return Types */
    private static final GenericType<ExecutionContextList> EXECUTION_CONTEXT_LIST =
            new GenericType<ExecutionContextList>() {};
    private static final GenericType<DeploymentList> DEPLOYMENT_LIST = new GenericType<DeploymentList>() {};
    private static final GenericType<Deployment> DEPLOYMENT = new GenericType<Deployment>() {};

    private final @NotOwning ApiClient m_apiClient;

    /**
     * Create the {@link ExecutionServiceClient} given an {@link ApiClient}
     *
     * @param apiClient the {@link ApiClient}
     */
    public ExecutionServiceClient(final @NotOwning ApiClient apiClient) {
        m_apiClient = apiClient;
    }

    /**
     * Retrieves the associated {@link ApiClient}.
     *
     * @return {@link ApiClient}
     */
    public @NotOwning ApiClient getApiClient() {
        return m_apiClient;
    }

    /**
     * The returned list will contain the execution contexts viewable by the user under the provided scope.
     *
     * @param scope The scope (accountId), (required)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<ExecutionContextList> getExecutionContexts(
        final String scope, final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(scope);

        final var requestPath =
            IPath.forPosix(EXECUTION_CONTEXTS_API_PATH).append(scope);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.GET, null, EXECUTION_CONTEXT_LIST);
    }

    /**
     * The returned list will only contain deployments that the current user can read.
     *
     * @param deploymentType The {@link DeploymentsEndpointSuffix} which should be included.
     *        If missing it will return all types. (required)
     * @param workflowUri The path or the ID of the workflow for which a job should be created. (required)
     * @param itemVersion The item version of the workflow. If 'most-recent' is provided the latest
     *                    version at the time when a job is being created will be used. (required)
     * @param requestBody The {@link DeploymentCreationBody} containing the deployment data. (required)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException id an I/O error occurred
     */
    public ApiResponse<Deployment> createDeployments(final DeploymentsEndpointSuffix deploymentType,
        final String workflowUri, final ItemVersion itemVersion, final DeploymentCreationBody requestBody,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(deploymentType);
        CheckUtils.checkArgumentNotNull(itemVersion);
        CheckUtils.checkArgumentNotNull(requestBody);

        final var requestPath = IPath.forPosix(DEPLOYMENTS_API_PATH).append(deploymentType.getValue());

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
            .withQueryParam(QUERY_PARAM_WORKFLOW_URI, workflowUri)
            .withQueryParam(getQueryParameter(itemVersion).orElse(null))
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.POST, requestBody, DEPLOYMENT);
    }

    /**
     * Modifies a single deployment.
     *
     * @param id The deployment id. (required)
     * @param requestBody The {@link DeploymentCreationBody} containing the new deployment data. (required)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException id an I/O error occurred
     */
    public ApiResponse<Deployment> updateDeployment(final String id, final DeploymentCreationBody requestBody,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);
        CheckUtils.checkArgumentNotNull(requestBody);

        final var requestPath = IPath.forPosix(DEPLOYMENTS_API_PATH).append(id);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.PUT, requestBody, DEPLOYMENT);
    }

    /**
     * The returned list will only contain deployments that the current user can read.
     *
     * @param scope The scope for which the deployments should be returned. (required)
     * @param deploymentType The {@link DeploymentsEndpointSuffix} which should be included.
     *        If missing it will return all types. (optional)
     * @param workflowUri An optional filter consisting of a workflow path or its ID
     *        for which the deployments shall be returned.
     * @param itemVersion An optional filter consisting of the item version of the workflow for which the
     *        deployments shall be returned. Only applicable if workflowUri is set.
     * @param q An optional filter to only show the deployments that match the filter. Only a single attribute
     *        can be used for filtering right now. Available attributes are:
     *        <ul>
     *          <li> workflowPath: the workflow path </li>
     *          <li> workflowId: the workflow id </li>
     *          <li> creator: the fully-qualified ID of the deployment's creator </li>
     *          <li> executionContextId: the fully-qualified ID of the execution context
     *               under which deployment was created </li>
     *        </ul>
     * @param sort An optional parameter for sorting. By default results are sorted in ascending order.
     *             If you prefix the attribute with - the sort order is descending. The default sorting is createdAt.
     *             Available attributes for sorting are:
     *        <ul>
     *          <li> workflowId: the workflow ID </li>
     *          <li> workflowPath: the workflow path </li>
     *          <li> itemVersion: the corresponding workflow's version </li>
     *          <li> creator: the fully-qualified ID of the deployment's creator </li>
     *          <li> executionContextId: the fully-qualified ID of the execution context
     *               under which deployment was created </li>
     *          <li> type: type of the deployment </li>
     *          <li> createdAt: the deployment's creation time stamp </li>
     *        </ul>
     * @param page    The page number to be displayed. (optional)
     * @param pagelen Number of jobs to be displayed on a single page. An optional parameter,
     *                if not provided a default value (usually 100) will be used. (optional)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException id an I/O error occurred
     */
    public ApiResponse<DeploymentList> getDeployments(final String scope,
        final DeploymentsEndpointSuffix deploymentType, final String workflowUri, final ItemVersion itemVersion,
        final String q, final String sort, final Integer page, final Integer pagelen,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(scope);

        final var requestPath = IPath.forPosix(DEPLOYMENTS_API_PATH).append(scope);
        if (deploymentType != null) {
            requestPath.append(deploymentType.getValue());
        }

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withQueryParam(QUERY_PARAM_WORKFLOW_URI, workflowUri)
            .withQueryParam(getQueryParameter(itemVersion).orElse(null))
            .withQueryParam(QUERY_PARAM_Q, q)
            .withQueryParam(QUERY_PARAM_SORT, sort)
            .withQueryParam(QUERY_PARAM_PAGE, page != null ? Integer.toString(page) : null)
            .withQueryParam(QUERY_PARAM_PAGELEN, pagelen != null ? Integer.toString(pagelen) : null)
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.GET, null, DEPLOYMENT_LIST);
    }

    /**
     * Deployments endpoint suffixes.
     */
    public enum DeploymentsEndpointSuffix {
        /** Workflow Group */
        REST(PATH_PIECE_REST), //
        /** Workflow */
        SCHEDULE(PATH_PIECE_SCHEDULES), //
        /** Component */
        DATA_APP(PATH_PIECE_DATA_APPS), //
        /** Data */
        TRIGGER(PATH_PIECE_TRIGGERS);

        private final String m_value;

        DeploymentsEndpointSuffix(final String value) {
            this.m_value = value;
        }

        @JsonValue
        private final String getValue() {
            return m_value;
        }

        @Override
        public String toString() {
            return String.valueOf(m_value);
        }

        /**
         * The end point suffix for the GET/deployments/{scope} end point.
         *
         * @param value the string value
         *
         * @return {@link DeploymentsEndpointSuffix}}
         */
        @JsonCreator
        public static DeploymentsEndpointSuffix fromValue(final String value) {
            for (DeploymentsEndpointSuffix b : DeploymentsEndpointSuffix.values()) {
                if (b.m_value.equals(value)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }
    }

    /**
     * Returns the given item version for the Execution service as a query parameter key-value pair, or
     * {@link Optional#empty()} if the argument was {@code null}.
     *
     * @param version {@code null}-able version to map to its string representation
     * @return query parameter key-value pair representing the given item version or {@link Optional#empty()} if
     *         argument was {@code null}
     */
    public static Optional<HTTPQueryParameter> getQueryParameter(final ItemVersion version) {
        return Optional.ofNullable(version) //
                .map(v -> v.match(//
                    () -> new HTTPQueryParameter(QUERY_PARAM_ITEM_VERSION, ITEM_VERSION_CURRENT_STATE_IDENTIFIER), //
                    () -> new HTTPQueryParameter(QUERY_PARAM_ITEM_VERSION, ITEM_VERSION_MOST_RECENT_IDENTIFIER), //
                    sv -> new HTTPQueryParameter(QUERY_PARAM_ITEM_VERSION, Integer.toString(sv)) //
                    ) //
                );
    }

}
