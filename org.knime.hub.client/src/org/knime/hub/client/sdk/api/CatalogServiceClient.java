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
 *   Feb 27, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.hub.CurrentState;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiClient.DownloadContentHandler;
import org.knime.hub.client.sdk.ApiClient.Method;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.HTTPQueryParameter;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.ent.CopyOrMoveRequestBody;
import org.knime.hub.client.sdk.ent.CreateNamedItemVersionRequestBody;
import org.knime.hub.client.sdk.ent.DownloadStatus;
import org.knime.hub.client.sdk.ent.NamedItemVersion;
import org.knime.hub.client.sdk.ent.NamedItemVersionList;
import org.knime.hub.client.sdk.ent.PreparedDownload;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.SpaceRenameRequestBody;
import org.knime.hub.client.sdk.ent.SpaceRequestBody;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.ent.UploadStarted;
import org.knime.hub.client.sdk.ent.UploadStatus;
import org.knime.hub.client.sdk.ent.UploadTarget;
import org.knime.hub.client.sdk.transfer.ArtifactDownloadStream;
import org.knime.hub.client.sdk.transfer.AsyncHubUploadStream;
import org.knime.hub.client.sdk.transfer.ItemID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

/**
 * Catalog client for KNIME Hub.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@SuppressWarnings("java:S107") // Number of parameters per endpoint is not controllable
public final class CatalogServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogServiceClient.class);

    /* API paths */
    private static final String REPOSITORY_API_PATH = "repository";
    private static final String UPLOAD_API_PATH = "uploads";
    private static final String DOWNLOAD_API_PATH = "downloads";
    private static final String SPACES_API_PATH = "spaces";

    /* Path pieces */
    private static final String PATH_PIECE_USERS = "Users";
    private static final String PATH_PIECE_DATA = ":data";
    private static final String PATH_PIECE_MANIFEST = "manifest";
    private static final String PATH_PIECE_STATUS = "status";
    private static final String PATH_PIECE_PARTS = "parts";
    private static final String PATH_PIECE_ARTIFACT = "artifact";
    private static final String PATH_PIECE_COPIES = "copies";
    private static final String PATH_PIECE_PATH = "path";
    private static final String PATH_PIECE_NAME = "name";
    private static final String PATH_PIECE_VERSIONS = "versions";

    /* Query parameters */
    private static final String QUERY_PARAM_FROM_REPOSITORY = "from-repository";
    private static final String QUERY_PARAM_MOVE = "move";
    private static final String QUERY_PARAM_VERSION = "version";
    private static final String QUERY_PARAM_SOFT_DELETE = "softDelete";
    private static final String QUERY_PARAM_DETAILS = "details";
    private static final String QUERY_PARAM_DEEP = "deep";
    private static final String QUERY_PARAM_SPACE_DETAILS = "spaceDetails";
    private static final String QUERY_PARAM_CONTRIB_SPACES = "contribSpaces";
    private static final String QUERY_PARAM_PART_NUMBER = "partNumber";
    private static final String QUERY_PARAM_LIMIT = "limit";

    /* Return Types */
    private static final GenericType<RepositoryItem> REPOSITORY_ITEM = new GenericType<RepositoryItem>() {};
    private static final GenericType<UploadStatus> UPLOAD_STATUS = new GenericType<UploadStatus>() {};
    private static final GenericType<UploadStarted> UPLOAD_STARTED = new GenericType<UploadStarted>() {};
    private static final GenericType<UploadTarget> UPLOAD_TARGET = new GenericType<UploadTarget>() {};
    private static final GenericType<PreparedDownload> PREPARED_DOWNLOAD = new GenericType<PreparedDownload>() {};
    private static final GenericType<DownloadStatus> DONWLOAD_STATUS = new GenericType<DownloadStatus>() {};
    private static final GenericType<NamedItemVersion> NAMED_ITEM_VERSION = new GenericType<NamedItemVersion>() {};
    private static final GenericType<NamedItemVersionList> NAMED_ITEM_VERSION_LIST =
            new GenericType<NamedItemVersionList>() {};

    /* Item version query parameter "special" values */
    private static final String ITEM_VERSION_MOST_RECENT_IDENTIFIER = "most-recent";
    private static final String ITEM_VERSION_CURRENT_STATE_IDENTIFIER = "current-state";

    private final @NotOwning ApiClient m_apiClient;

    /**
     * Create the {@link CatalogServiceClient} given an {@link ApiClient}
     *
     * @param apiClient the {@link ApiClient}
     */
    public CatalogServiceClient(final @NotOwning ApiClient apiClient) {
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
     * Creates the metadata of a repository item at the given path. Currently, only workflow groups and spaces are
     * supported. To create a new workflow group, issue a PUT request to a path where groups are allowed (e.g. not at
     * the space level). If the group already exists, it will be returned. Groups are not allowed to be created in a
     * user directory, and can only be created within spaces by non-admin users. To create a new space, issue a PUT
     * request with a request to a space path (i.e. in a user directory) with an optional request body specifying space
     * metadata. If the space already exists an error will be returned. Space metadata properties not specified in the
     * request body will be set to their default values. If no request body is specified, default values will be used.
     *
     * @param accountId The ID of the account the repository item is associated with (required)
     * @param subPath The "/" delimited path to the resource below the account root level (required)
     * @param spaceRequestBody Optional item metadata (optional)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> createItemByCanonicalPath(final String accountId, final IPath subPath,
        final SpaceRequestBody spaceRequestBody, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(accountId);
        CheckUtils.checkArgumentNotNull(subPath);

        final var requestPath =
            IPath.forPosix(REPOSITORY_API_PATH).append(PATH_PIECE_USERS).append(accountId).append(subPath);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.PUT, spaceRequestBody, REPOSITORY_ITEM);
    }

    /**
     * Creates the metadata of a repository item at the given path. Currently, only workflow groups and spaces are
     * supported. To create a new workflow group, issue a PUT request to a path where groups are allowed (e.g. not at
     * the space level). If the group already exists, it will be returned. Groups are not allowed to be created in a
     * user directory, and can only be created within spaces by non-admin users. To create a new space, issue a PUT
     * request with a request to a space path (i.e. in a user directory) with an optional request body specifying space
     * metadata. If the space already exists an error will be returned. Space metadata properties not specified in the
     * request body will be set to their default values. If no request body is specified, default values will be used.
     *
     * @param path The absolute path to the new workflow group or space (required)
     * @param spaceRequestBody Optional item metadata (optional)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> createItemByPath(final IPath path, final SpaceRequestBody spaceRequestBody,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(path);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(path);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.PUT, spaceRequestBody, REPOSITORY_ITEM);
    }

    /**
     * Deletes an item (workflow, data file, a whole workflow group) from the repository.
     *
     * @param path The absolute path to the repository item. It always starts with a * and does not change even if the
     *            repository item is renamed or moved. (required)
     * @param softDelete Optional parameter that enables soft deletion of repository items, which means that deleted
     *            items get moved to the trash bin. (optional, default to false)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<Void> deleteItemByPath(final IPath path, final boolean softDelete,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(path);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(path);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.WILDCARD_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_SOFT_DELETE, softDelete ? Boolean.toString(softDelete) : null) //
            .invokeAPI(requestPath, Method.DELETE, null);
    }

    /**
     * Deletes an item (workflow, data file, a whole workflow group) from the repository.
     *
     * @param accountId The ID of the account the repository item is associated with (required)
     * @param subPath The "/" delimited path to the resource below the account root level (required)
     * @param softDelete Optional parameter that enables soft deletion of repository items, which means that deleted
     *            items get moved to the trash bin. (optional, default to false)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<Void> deleteItemByCanonicalPath(final String accountId, final IPath subPath,
        final boolean softDelete, final Map<String, String> additionalHeaders)
                throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(accountId);
        CheckUtils.checkArgumentNotNull(subPath);

        final var requestPath =
            IPath.forPosix(REPOSITORY_API_PATH).append(PATH_PIECE_USERS).append(accountId).append(subPath);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.WILDCARD_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_SOFT_DELETE, softDelete ? Boolean.toString(softDelete) : null) //
            .invokeAPI(requestPath, Method.DELETE, null);
    }

    /**
     * Deletes an item (workflow, data file, a whole workflow group) from the repository.
     *
     * @param id The items unique ID. It always starts with a * and does not change even if the repository item is
     *            renamed or moved. (required)
     * @param softDelete Optional parameter that enables soft deletion of repository items, which means that deleted
     *            items get moved to the trash bin. (optional, default to false)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<Void> deleteItemById(final String id, final boolean softDelete,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.WILDCARD_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withQueryParam(QUERY_PARAM_SOFT_DELETE, softDelete ? Boolean.toString(softDelete) : null) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.DELETE, null);
    }

    /**
     * Downloads the given repository item. The returned content depends on the item type. If its a workflow then a ZIP
     * archive is returned. If its a data file then the file will be returned as is with an auto-guessed content types
     * (based on the file extension).
     *
     * @param accountId The ID of the account the repository item is associated with (required)
     * @param subPath The "/" delimited path to the resource below the account root level (required)
     * @param version Optional version of the item to retrieve, {@code null} is synonymous with
     *            {@link CurrentState#getInstance() current-state}. (optional, default to current-state)
     * @param responseType The type of the response body (required).
     * @param contentHandler The content handler to out source the given input stream (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws IOException if the operation had an I/O error
     * @throws CancelationException if the operation was canceled
     */
    public <R> ApiResponse<R> downloadItemByCanonicalPath(final String accountId, final IPath subPath,
        final ItemVersion version, final MediaType responseType, final DownloadContentHandler<R> contentHandler,
        final Map<String, String> additionalHeaders)
                throws IOException, CancelationException {
        CheckUtils.checkArgumentNotNull(contentHandler);
        CheckUtils.checkArgumentNotNull(responseType);
        CheckUtils.checkArgumentNotNull(accountId);
        CheckUtils.checkArgumentNotNull(subPath);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(PATH_PIECE_USERS).append(accountId)
            .append(subPath + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(responseType, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(getQueryParameter(version).orElse(null)) //
            .invokeAPI(requestPath, Method.GET, null, contentHandler);
    }

    /**
     * Downloads the given repository item. The returned content depends on the items type. If its a workflow then a ZIP
     * archive is returned. If its a data file then the file will be returned as is with an auto-guessed content types
     * (based on the file extension).
     *
     * @param id The repository items unique ID. It always starts with a * and does not change even if the repository
     *            item is renamed or moved. May also be a concatenation of path followed by the "~" character and the ID
     *            without the leading "*" character. This occurs when the request originates from an older AP that
     *            cannot handle the new URI format which adds the ID to the end of the path. (required)
     * @param version Optional version of the item to retrieve, {@code null} is synonymous with
     *            {@link CurrentState#getInstance() current-state}. (optional, default to current-state)
     * @param responseType The type of the response body (required).
     * @param additionalHeaders Map of additional headers
     * @param contentHandler The content handler to out source the given input stream (required).
     * @return {@link ApiResponse}
     *
     * @throws IOException if the operation had an I/O error
     * @throws CancelationException if the operation was canceled
     */
    public <R> ApiResponse<R> downloadItemById(final String id, final ItemVersion version,
        final MediaType responseType, final Map<String, String> additionalHeaders,
        final DownloadContentHandler<R> contentHandler)
        throws IOException, CancelationException {
        CheckUtils.checkArgumentNotNull(contentHandler);
        CheckUtils.checkArgumentNotNull(responseType);
        CheckUtils.checkArgumentNotNull(id);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(responseType, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(getQueryParameter(version).orElse(null)) //
            .invokeAPI(requestPath, Method.GET, null, contentHandler);
    }

    /**
     * Downloads the given repository item. The returned content depends on the items type. If its a workflow then a ZIP
     * archive is returned. If its a data file then the file will be returned as is with an auto-guessed content types
     * (based on the file extension).
     *
     * @param path The absolute path to the repository item. (required)
     * @param version Optional version of the item to retrieve, {@code null} is synonymous with
     *            {@link CurrentState#getInstance() current-state}. (optional, default to current-state)
     * @param responseType The type of the response body (required).
     * @param contentHandler The content handler to out source the given input stream (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws IOException if an I/O error occurred
     * @throws CancelationException if the operation was canceled
     */
    public <R> ApiResponse<R> downloadItemByPath(final IPath path, final ItemVersion version,
        final MediaType responseType, final DownloadContentHandler<R> contentHandler,
        final Map<String, String> additionalHeaders)
        throws IOException, CancelationException {
        CheckUtils.checkArgumentNotNull(contentHandler);
        CheckUtils.checkArgumentNotNull(responseType);
        CheckUtils.checkArgumentNotNull(path);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(path + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(responseType, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(getQueryParameter(version).orElse(null)) //
            .invokeAPI(requestPath, Method.GET, null, contentHandler);
    }

    /**
     * Returns the repository item at the given canonical path in the catalog
     *
     * @param accountId The ID of the account the repository item is associated with (required)
     * @param subPath The "/" delimited path to the resource below the account root level (required)
     * @param details Specifies whether details should be shown and in what form (full details, aggregated, ...).
     *            (optional)
     * @param deep Optional query parameter which enables deep listing of all children. (optional)
     * @param spaceDetails Optional query parameter which when true provides additional information about a space (i.e.
     *            kudos and stats) (optional)
     * @param contribSpaces An optional query parameter which determines if the spaces a user can contribute to are
     *            included when requesting a users directory, and the format these spaces should be written in. This
     *            parameter is ignored if another other than a users directory is requested. (optional, default to none)
     * @param version Optional version of the item to retrieve, {@code null} is synonymous with
     *            {@link CurrentState#getInstance() current-state}. (optional, default to current-state)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> getRepositoryItemByCanonicalPath(final String accountId, final IPath subPath,
        final String details, final boolean deep, final boolean spaceDetails, final String contribSpaces,
        final ItemVersion version, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(accountId);
        CheckUtils.checkArgumentNotNull(subPath);

        final var requestPath =
            IPath.forPosix(REPOSITORY_API_PATH).append(PATH_PIECE_USERS).append(accountId).append(subPath);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(getQueryParameter(version).orElse(null)) //
            .withQueryParams(metaDataQueryParameters(details, deep, spaceDetails, contribSpaces)) //
            .invokeAPI(requestPath, Method.GET, null, REPOSITORY_ITEM);
    }

    /**
     * Returns the repository item at the given path in the server repository.
     *
     * @param path The absolute path to the repository item. (required)
     * @param details Specifies whether details should be shown and in what form (full details, aggregated, ...).
     *            (optional)
     * @param deep Optional query parameter which enables deep listing of all children. (optional)
     * @param spaceDetails Optional query parameter which when true provides additional information about a space (i.e.
     *            kudos and stats) (optional)
     * @param contribSpaces An optional query parameter which determines if the spaces a user can contribute to are
     *            included when requesting a users directory, and the format these spaces should be written in. This
     *            parameter is ignored if another other than a users directory is requested. (optional, default to none)
     * @param version Optional version of the item to retrieve, {@code null} is synonymous with
     *            {@link CurrentState#getInstance() current-state}. (optional, default to current-state)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> getRepositoryItemByPath(final IPath path, final String details,
        final boolean deep, final boolean spaceDetails, final String contribSpaces, final ItemVersion version,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(path);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(path);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(getQueryParameter(version).orElse(null)) //
            .withQueryParams(metaDataQueryParameters(details, deep, spaceDetails, contribSpaces)) //
            .invokeAPI(requestPath, Method.GET, null, REPOSITORY_ITEM);
    }

    /**
     * Returns the repository item at the given path or with the given ID in the server repository.
     *
     * @param id The items unique ID. It always starts with a * and does not change even if the repository item is
     *            renamed or moved. May also be a concatenation of path followed by the "~"; character and the ID
     *            without the leading "*" character. This occurs when the request originates from an older AP that
     *            cannot handle the new URI format which adds the ID to the end of the path. (required)
     * @param details Specifies whether details should be shown and in what form (full details, aggregated, ...).
     *            (optional)
     * @param deep Optional query parameter which enables deep listing of all children. (optional)
     * @param spaceDetails Optional query parameter which when true provides additional information about a space (i.e.
     *            kudos and stats) (optional)
     * @param contribSpaces An optional query parameter which determines if the spaces a user can contribute to are
     *            included when requesting a users directory, and the format these spaces should be written in. This
     *            parameter is ignored if another other than a users directory is requested. (optional, default to none)
     * @param version Optional version of the item to retrieve, {@code null} is synonymous with
     *            {@link CurrentState#getInstance() current-state}. (optional, default to current-state)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> getRepositoryItemById(final String id, final String details,
        final boolean deep, final boolean spaceDetails, final String contribSpaces, final ItemVersion version,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);


        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id);
        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(getQueryParameter(version).orElse(null)) //
            .withQueryParams(metaDataQueryParameters(details, deep, spaceDetails, contribSpaces)) //
            .invokeAPI(requestPath, Method.GET, null, REPOSITORY_ITEM);
    }

    private static Map<String, String> metaDataQueryParameters(final String details, final boolean deep,
        final boolean spaceDetails, final String contribSpaces) {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QUERY_PARAM_DETAILS, details);
        queryParams.put(QUERY_PARAM_DEEP, deep ? Boolean.toString(deep) : null);
        queryParams.put(QUERY_PARAM_SPACE_DETAILS, spaceDetails ? Boolean.toString(spaceDetails) : null);
        queryParams.put(QUERY_PARAM_CONTRIB_SPACES, contribSpaces);
        return queryParams;
    }

    /**
     * List all versions for a certain repository item.
     *
     * @param id The items unique ID. It always starts with a * and does not change even if the repository item is
     *            renamed or moved. May also be a concatenation of path followed by the "~"; character and the ID
     *            without the leading "*" character. This occurs when the request originates from an older AP that
     *            cannot handle the new URI format which adds the ID to the end of the path. (required)
     * @param limit Maximum number of versions returned in the response. All versions will be returned by using -1.
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<NamedItemVersionList> getItemVersions(final String id, final Integer limit,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id).append(PATH_PIECE_VERSIONS);
        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_LIMIT, Integer.toString(limit)) //
            .invokeAPI(requestPath, Method.GET, null, NAMED_ITEM_VERSION_LIST);
    }

    /**
     * Creates a new version for the repository item with the given ID.
     *
     * @param id The items unique ID. It always starts with a * and does not change even if the repository item is
     *            renamed or moved. May also be a concatenation of path followed by the "~"; character and the ID
     *            without the leading "*" character. This occurs when the request originates from an older AP that
     *            cannot handle the new URI format which adds the ID to the end of the path. (required)
     * @param createNamedItemVersionRequestBody The request body to create a new named item version
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<NamedItemVersion> createItemVersion(final String id,
        final CreateNamedItemVersionRequestBody createNamedItemVersionRequestBody,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);
        CheckUtils.checkArgumentNotNull(createNamedItemVersionRequestBody);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id).append(PATH_PIECE_VERSIONS);
        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.POST, createNamedItemVersionRequestBody, NAMED_ITEM_VERSION);
    }

    /**
     * Performs a server side copy of a repository item by canonical path. Depending on the content type of the request
     * the server side copy is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting preservers the
     * KNIME ID of the target repository item.
     *
     * @param accountId The ID of the account the repository item is associated with (required)
     * @param subPath The "/" delimited path to the resource below the account root level (required)
     * @param fromRepository Source canonical path of the repository item which gets copied (required).
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> serverCopyByCanonicalPath(final String accountId, final IPath subPath,
        final String fromRepository, final MediaType contentType, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(accountId);
        CheckUtils.checkArgumentNotNull(subPath);
        CheckUtils.checkArgumentNotNull(fromRepository);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(PATH_PIECE_USERS).append(accountId)
            .append(subPath + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_FROM_REPOSITORY, fromRepository) //
            .withQueryParam(QUERY_PARAM_MOVE, Boolean.FALSE.toString()) //
            .invokeAPI(requestPath, Method.PUT, null, REPOSITORY_ITEM);
    }

    /**
     * Performs a server side move of a repository item by canonical path. Depending on the content type of the request
     * the server side move is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting updates the
     * KNIME ID of the target repository item with the KNIME ID of the source repository item.
     *
     * @param accountId The ID of the account the repository item is associated with (required)
     * @param subPath The "/" delimited path to the resource below the account root level (required)
     * @param fromRepository Source canonical path of the repository item which gets moved (required).
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> serverMoveByCanonicalPath(final String accountId, final IPath subPath,
        final String fromRepository, final MediaType contentType, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(accountId);
        CheckUtils.checkArgumentNotNull(subPath);
        CheckUtils.checkArgumentNotNull(fromRepository);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(PATH_PIECE_USERS).append(accountId)
            .append(subPath + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_FROM_REPOSITORY, fromRepository) //
            .withQueryParam(QUERY_PARAM_MOVE, Boolean.TRUE.toString()) //
            .invokeAPI(requestPath, Method.PUT, null, REPOSITORY_ITEM);
    }

    /**
     * Uploads a workflow, component, workflow group or data file by canonical path. Depending on the content type of
     * the request the upload is either a workflow/component (application/vnd.knime.workflow+zip), workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting preserves the
     * KNIME ID of the target repository item.
     *
     * @param accountId The ID of the account the repository item is associated with (required)
     * @param subPath The "/" delimited path to the resource below the account root level (required)
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @param dataToTransfer stream of bytes to be uploaded
     * @param contentLength number of bytes that will be written, or {@code -1} if unknown
     * @return {@link ApiResponse}
     *
     * @throws IOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> uploadItemByCanonicalPath(final String accountId, final IPath subPath,
        final MediaType contentType, final Map<String, String> additionalHeaders, final InputStream dataToTransfer,
        final long contentLength) throws IOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(accountId);
        CheckUtils.checkArgumentNotNull(subPath);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(PATH_PIECE_USERS).append(accountId)
            .append(subPath + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.PUT, dataToTransfer, contentLength, REPOSITORY_ITEM);
    }

    /**
     * Performs a server side copy of a repository item by ID. Depending on the content type of the request the server
     * side copy is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting preservers the
     * KNIME ID of the target repository item.
     *
     * <p><b>Note:</b> This API call always overwrites the target item.
     * Prefer using its replacement method to influence overwrite behavior.</p>
     *
     * @param id The repository items unique ID. It always starts with a * and does not change even if the repository
     *            item is renamed or moved. (required)
     * @param fromRepository Source ID of the repository item which gets copied (required).
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     * @deprecated use {@link #serverCopyById(String, IPath, boolean, Map)}
     */
    @Deprecated
    public ApiResponse<RepositoryItem> serverCopyById(final String id, final String fromRepository,
        final MediaType contentType, final Map<String, String> additionalHeaders)
                throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(id);
        CheckUtils.checkArgumentNotNull(fromRepository);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_FROM_REPOSITORY, fromRepository) //
            .withQueryParam(QUERY_PARAM_MOVE, Boolean.FALSE.toString()) //
            .invokeAPI(requestPath, Method.PUT, null, REPOSITORY_ITEM);
    }

    /**
     * Performs a server side copy of a repository item by ID. Depending on the content type of the request the server
     * side copy is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting preservers the
     * KNIME ID of the target repository item.
     *
     * @param id The repository items unique ID. It always starts with a * and does not change even if the repository
     *            item is renamed or moved. (required)
     * @param canonicalPath The new canonical path of the repository item
     * @param force Whether to force the copy or move operation, i.e. overwrite existing items
     * @param ifTargetMatch the If-Target-Match header
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> serverCopyById(final String id, final IPath canonicalPath,
        final boolean force, final String ifTargetMatch, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);
        CheckUtils.checkArgumentNotNull(canonicalPath);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id).append(PATH_PIECE_COPIES);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.POST, CopyOrMoveRequestBody.builder()
                    .withCanoncialPath(canonicalPath.toString()).withForce(force)
                    .withIfTargetMatch(ifTargetMatch)
                    .build(), REPOSITORY_ITEM);
    }

    /**
     * Performs a server side move of a repository item by ID. Depending on the content type of the request the server
     * side move is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting updates the
     * KNIME ID of the target repository item with the KNIME ID of the source repository item.
     *
     * <p><b>Note:</b> This API call always overwrites the target item.
     * Prefer using its replacement method to influence overwrite behavior.</p>
     *
     * @param id The repository items unique ID. It always starts with a * and does not change even if the repository
     *            item is renamed or moved. (required)
     * @param fromRepository Source ID of the repository item which gets moved (required).
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     * @deprecated use {@link #serverMoveById(String, IPath, boolean, Map)}
     */
    @Deprecated
    public ApiResponse<RepositoryItem> serverMoveById(final String id, final String fromRepository,
        final MediaType contentType, final Map<String, String> additionalHeaders)
                throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(id);
        CheckUtils.checkArgumentNotNull(fromRepository);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_FROM_REPOSITORY, fromRepository) //
            .withQueryParam(QUERY_PARAM_MOVE, Boolean.TRUE.toString()) //
            .invokeAPI(requestPath, Method.PUT, null, REPOSITORY_ITEM);
    }

    /**
     * Performs a server side move of a repository item by ID. Depending on the content type of the request the server
     * side move is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting updates the
     * KNIME ID of the target repository item with the KNIME ID of the source repository item.
     *
     * @param id The repository items unique ID. It always starts with a * and does not change even if the repository
     *            item is renamed or moved. (required)
     * @param canonicalPath The new canonical path of the repository item
     * @param force Whether to force the copy or move operation, i.e. overwrite existing items
     * @param ifTargetMatch The If-Target-Match header
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> serverMoveById(final String id, final IPath canonicalPath,
        final boolean force, final String ifTargetMatch, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);
        CheckUtils.checkArgumentNotNull(canonicalPath);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id).append(PATH_PIECE_PATH);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE)//
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.PUT, CopyOrMoveRequestBody.builder()
                .withCanoncialPath(canonicalPath.toString()).withForce(force)
                .withIfTargetMatch(ifTargetMatch)
                .build(), REPOSITORY_ITEM);
    }

    /**
     * Uploads a workflow, component, workflow group or data file by ID. Depending on the content type of the request
     * the upload is either a workflow/component (application/vnd.knime.workflow+zip), workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting preserves the
     * KNIME ID of the target repository item.
     *
     * @param id The repository items unique ID. It always starts with a * and does not change even if the repository
     *            item is renamed or moved. (required)
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @param dataToTransfer stream of bytes to be uploaded
     * @param contentLength number of bytes that will be written, or {@code -1} if unknown
     * @return {@link ApiResponse}
     *
     * @throws IOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> uploadItemById(final String id, final MediaType contentType,
        final Map<String, String> additionalHeaders, final InputStream dataToTransfer, final long contentLength)
        throws IOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(id);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.PUT, dataToTransfer, contentLength, REPOSITORY_ITEM);
    }

    /**
     * Performs a server side copy of a repository item by path. Depending on the content type of the request the server
     * side copy is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting preservers the
     * KNIME ID of the target repository item.
     *
     * <p><b>Note:</b> This API call always overwrites the target item.
     * Prefer using its replacement method to influence overwrite behavior.</p>
     *
     * @param path The absolute path to the repository item. (required)
     * @param fromRepository Source path of the repository item which gets copied (required).
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     * @deprecated use {@link #serverCopyById(String, IPath, boolean, Map)}
     */
    @Deprecated
    public ApiResponse<RepositoryItem> serverCopyByPath(final IPath path, final String fromRepository,
        final MediaType contentType, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(path);
        CheckUtils.checkArgumentNotNull(fromRepository);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(path + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_FROM_REPOSITORY, fromRepository) //
            .withQueryParam(QUERY_PARAM_MOVE, Boolean.FALSE.toString()) //
            .invokeAPI(requestPath, Method.PUT, null, REPOSITORY_ITEM);
    }

    /**
     * Performs a server side move of a repository item by path. Depending on the content type of the request the server
     * side move is either a workflow/component (application/vnd.knime.workflow+zip) workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting updates the
     * KNIME ID of the target repository item with the KNIME ID of the source repository item.
     *
     * <p><b>Note:</b> This API call always overwrites the target item.
     * Prefer using its replacement method to influence overwrite behavior.</p>
     *
     * @param path The absolute path to the repository item. (required)
     * @param fromRepository Source path of repository item which gets moved (required).
     * @param contentType The content type of the request body (required).
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     * @deprecated use {@link #serverMoveById(String, IPath, boolean, Map)}
     */
    @Deprecated
    public ApiResponse<RepositoryItem> serverMoveByPath(final IPath path, final String fromRepository,
        final MediaType contentType, final Map<String, String> additionalHeaders)
                throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(path);
        CheckUtils.checkArgumentNotNull(fromRepository);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(path + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_FROM_REPOSITORY, fromRepository) //
            .withQueryParam(QUERY_PARAM_MOVE, Boolean.TRUE.toString()) //
            .invokeAPI(requestPath, Method.PUT, null, REPOSITORY_ITEM);
    }

    /**
     * Uploads a workflow, component, workflow group or data file by path. Depending on the content type of the request
     * the upload is either a workflow/component (application/vnd.knime.workflow+zip), workflow group
     * (application/vnd.knime.workflow-group+zip) or a data file (all other content types). Overwriting preserves the
     * KNIME ID of the target repository item.
     *
     * @param path The absolute path to the repository item. (required)
     * @param contentType The content type of the request body (required)
     * @param additionalHeaders Map of additional headers
     * @param dataToTransfer stream of bytes to be uploaded
     * @param contentLength number of bytes that will be written, or {@code -1} if unknown
     * @return {@link ApiResponse}
     *
     * @throws IOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> uploadItemByPath(final IPath path, final MediaType contentType,
        final Map<String, String> additionalHeaders, final @Owning InputStream dataToTransfer, final long contentLength)
        throws IOException {
        CheckUtils.checkArgumentNotNull(contentType);
        CheckUtils.checkArgumentNotNull(path);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(path + PATH_PIECE_DATA);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(contentType) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.PUT, dataToTransfer, contentLength, REPOSITORY_ITEM);
    }

    /**
     * Request that the upload process be cancelled.
     *
     * @param uploadId The ID of the upload process (required)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<Void> cancelUpload(final String uploadId, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(uploadId);

        final var requestPath = IPath.forPosix(UPLOAD_API_PATH).append(uploadId);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.WILDCARD_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.DELETE, null);
    }

    /**
     * Prepares the upload of a repository item as a direct child of the item with the given ID, and with the given
     * content type. This will create any necessary nested Workflow Groups lazily. Additionally, this endpoint will lock
     * all requested paths until the upload completes.
     *
     * @param parentId The ID of the parent item the artifact will be uploaded into. (required)
     * @param requestBody The request body (optional)
     * @param readTimeout The read timeout
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<UploadStarted> initiateUpload(final String parentId, final UploadManifest requestBody,
        final Duration readTimeout, final Map<String, String> additionalHeaders)
                throws HubFailureIOException {
        LOGGER.atDebug() //
            .addArgument(() -> requestBody.getItems().size()) //
            .log("Initiating upload of {} items");

        CheckUtils.checkArgumentNotNull(parentId);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(parentId).append(PATH_PIECE_MANIFEST);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withReadTimeout(readTimeout) //
            .invokeAPI(requestPath, Method.POST, requestBody, UPLOAD_STARTED);
    }

    /**
     * Retrieves the status of an asynchronous upload process
     *
     * @param uploadId The ID of the upload process (required)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<UploadStatus> pollUploadStatus(final String uploadId,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(uploadId);

        final var requestPath = IPath.forPosix(UPLOAD_API_PATH).append(uploadId).append(PATH_PIECE_STATUS);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.GET, null, UPLOAD_STATUS);
    }

    /**
     * Report that the upload with the given ID has been finished.
     *
     * @param uploadId The ID of the upload process (required)
     * @param requestBody The request body (optional)
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<Void> reportUploadFinished(final String uploadId, final Map<Integer, String> requestBody,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(uploadId);

        final var requestPath = IPath.forPosix(UPLOAD_API_PATH).append(uploadId);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.POST, requestBody);
    }

    /**
     * Request an additional part upload for the upload with the given ID.
     *
     * @param uploadId The ID of the upload process (required)
     * @param partNumber The part number of the next part (required)
     * @param additionalHeaders Map of additional parameters
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<UploadTarget> requestPartUpload(final String uploadId, final Integer partNumber,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(uploadId);
        CheckUtils.checkArgumentNotNull(partNumber);

        final var requestPath = IPath.forPosix(UPLOAD_API_PATH).append(uploadId).append(PATH_PIECE_PARTS);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(QUERY_PARAM_PART_NUMBER, Integer.toString(partNumber)) //
            .invokeAPI(requestPath, Method.POST, null, UPLOAD_TARGET);
    }

    /**
     * Creates an asynchronous upload stream to the hub. This upload supports single workflows,
     * components and data files. Workflow group upload is not supported.
     *
     * @param itemName the relative path of the item in the parent group
     * @param isWorkflowLike <code>true</code> if the item which is uploaded is a workflow or component
     * @param parentId the ID of the parent group
     * @param parentEtag the entity tag of the parent group
     * @param additionalHeaders additional header parameters
     *
     * @return {@link AsyncHubUploadStream}
     * @throws HubFailureIOException if an I/O error occurred during the upload
     */
    public @Owning AsyncHubUploadStream createAsyncHubUploadStream(final String itemName, final boolean isWorkflowLike,
        final String parentId, final EntityTag parentEtag, final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        return AsyncHubUploadStream.create(this, additionalHeaders, parentId, parentEtag, itemName, isWorkflowLike, -1);
    }

    /**
     * Creates an artifact download stream from a hub. This downloads single repository items.
     *
     * @param itemId the ID of the repository item to download
     * @param version the {@link ItemVersion} of the repository item
     * @param additionalHeaders additional headers for the download process
     * @param cancelChecker called to find out whether or not this method should be canceled ({@code true} -> cancel)
     *
     * @return {@link ArtifactDownloadStream}
     * @throws IOException if an I/O error occurred during the download
     * @throws CancelationException if creating the download was canceled
     */
    public @Owning ArtifactDownloadStream createArtifactDownloadStream(final ItemID itemId, final ItemVersion version,
        final Map<String, String> additionalHeaders, final BooleanSupplier cancelChecker)
        throws IOException, CancelationException {
        return ArtifactDownloadStream.create(this, additionalHeaders, itemId, version, cancelChecker);
    }

    /**
     * Prepares the download of the repository item with the given ID. If the repository item is a workflow group,
     * space, or account root, it will be downloadable as a single archive.
     *
     * @param id The ID of the repository item whose download to prepare.
     * @param version Optional version of the item to retrieve, {@code null} is synonymous with
     *        {@link CurrentState#getInstance() current-state}. (optional, default to current-state)
     * @param additionalHeaders additional header parameters
     * @return {@link ApiResponse}
     * @throws HubFailureIOException if an I/O error occurred during the request
     */
    public ApiResponse<PreparedDownload> prepareDownload(final String id, final ItemVersion version,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);

        final var requestPath = IPath.forPosix(REPOSITORY_API_PATH).append(id).append(PATH_PIECE_ARTIFACT);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .withQueryParam(getQueryParameter(version).orElse(null)) //
            .invokeAPI(requestPath, Method.GET, null, PREPARED_DOWNLOAD);
    }

    /**
     * Retrieves the status of the download with the given ID.
     *
     * @param id The ID of the repository item whose download to prepare.
     * @param additionalHeaders additional header parameters
     * @return {@link ApiResponse}
     * @throws HubFailureIOException if an I/O error occurred during the request
     */
    public ApiResponse<DownloadStatus> pollDownloadStatus(final String id,
        final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);

        final var requestPath = IPath.forPosix(DOWNLOAD_API_PATH).append(id).append(PATH_PIECE_STATUS);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.GET, null, DONWLOAD_STATUS);
    }

    /**
     * Renames the space with the given ID.
     *
     * @param id The ID of the space to rename
     * @param name The new name of the space
     * @param force Whether to force the space rename, i.e. overwrite existing items
     * @param ifTargetMatch The If-Target-Match header
     * @param additionalHeaders additional header parameters
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<RepositoryItem> renameSpace(final String id, final String name, final boolean force,
        final String ifTargetMatch, final Map<String, String> additionalHeaders) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(id);
        CheckUtils.checkArgumentNotNull(name);

        final var requestPath = IPath.forPosix(SPACES_API_PATH).append(id).append(PATH_PIECE_NAME);

        return m_apiClient.createApiRequest() //
                .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
                .withContentTypeHeader(MediaType.APPLICATION_JSON_TYPE) //
                .withHeaders(additionalHeaders) //
                .invokeAPI(requestPath, Method.PUT, SpaceRenameRequestBody.builder()
                    .withName(name)
                    .withForce(force)
                    .withIfTargetMatch(ifTargetMatch)
                    .build(), REPOSITORY_ITEM);
    }

    /**
     * Returns the given item version for the Catalog service as a query parameter key-value pair, or
     * {@link Optional#empty()} if the argument was {@code null}.
     *
     * @param version {@code null}-able version to map to its string representation
     * @return query parameter key-value pair representing the given item version or {@link Optional#empty()} if
     *         argument was {@code null}
     */
    public static Optional<HTTPQueryParameter> getQueryParameter(final ItemVersion version) {
        return Optional.ofNullable(version) //
                .map(v -> v.match(//
                    () -> new HTTPQueryParameter(QUERY_PARAM_VERSION, ITEM_VERSION_CURRENT_STATE_IDENTIFIER), //
                    () -> new HTTPQueryParameter(QUERY_PARAM_VERSION, ITEM_VERSION_MOST_RECENT_IDENTIFIER), //
                    sv -> new HTTPQueryParameter(QUERY_PARAM_VERSION, Integer.toString(sv)) //
                    ) //
                );
    }

}
