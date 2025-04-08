/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Jun 9, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.annotation.NotOwning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiClient.DownloadContentHandler;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.Result.Success;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.RepositoryItem.RepositoryItemType;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.ent.UploadStarted;
import org.knime.hub.client.sdk.ent.UploadStatus;
import org.knime.hub.client.sdk.ent.UploadTarget;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.RuntimeDelegate;
import jakarta.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

/**
  * Wrapper for all REST endpoint calls needed for catalog operations.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public class CatalogServiceClient {

    static final HeaderDelegate<EntityTag> ETAG_DELEGATE =
            RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);

    /** Read timeout for expensive operations like {@link #initiateUpload(ItemID, UploadManifest, EntityTag)}. */
    private static final Duration SLOW_OPERATION_READ_TIMEOUT = Duration.ofMinutes(15);

    private static final String KNIME_SERVER_NAMESPACE = "knime";

    /** Maximum number of pre-fetched upload URLs per upload. */
    public static final int MAX_NUM_PREFETCHED_UPLOAD_PARTS = 500;

    /** Relation that points to the endpoint for initiating an async upload flow. */
    public static final String INITIATE_UPLOAD =  "%s:initiate-upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that allows the requester to poll the status of an item upload. */
    public static final String UPLOAD_STATUS =  "%s:upload-status".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that allows the requester to request an upload part. */
    public static final String CREATE_UPLOAD_PART =  "%s:create-upload-part".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that allows the requester to notify Catalog Service that an item upload is completed. */
    public static final String COMPLETE_UPLOAD_PART =  "%s:complete-upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that allows the requester to abort an item upload. */
    public static final String ABORT_UPLOAD_PART =  "%s:abort-upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items download control */
    public static final String DOWNLOAD = "%s:download".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items upload control */
    public static final String UPLOAD = "%s:upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items edit control */
    public static final String EDIT = "edit";

    private static final String COULD_NOT_AUTHORIZE = "Could not authorize Hub REST call: ";

    /** Media type for a KNIME Workflow */
    public static final MediaType KNIME_WORKFLOW_MEDIA_TYPE =
            new MediaType("application", "vnd.knime.workflow+zip");
    /** Media type for a KNIME Workflow Group */
    public static final MediaType KNIME_WORKFLOW_GROUP_MEDIA_TYPE =
            new MediaType("application", "vnd.knime.workflow-group+zip");

    /**
     * Target description for a file HTTP download.
     *
     * @param name item name
     * @param type item type
     * @param url download URL
     */
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DownloadTarget(String name, RepositoryItem.RepositoryItemType type, URL url) {
    }

    /**
     * Repository item with associated {@code null}-able etag.
     *
     * @param item non-{@code null} repository item
     * @param etag entity tag
     */
    public record TaggedRepositoryItem(RepositoryItem item, EntityTag etag) {
        /**
         * Constructor.
         *
         * @param item non-{@code null} item
         * @param etag {@code null}-able etag
         */
        public TaggedRepositoryItem {
            CheckUtils.checkNotNull(item);
        }
    }

    @NotOwning
    private final HubClientAPI m_hubClient;

    private final Map<String, String> m_additionalHeaders;

    /**
     * Creates a new catalog service client using the given Hub client.
     *
     * @param hubClient {@link HubClientAPI}
     * @param additionalHeaders additional header parameters for up and download
     */
    public CatalogServiceClient(final HubClientAPI hubClient, final Map<String, String> additionalHeaders) {
        m_hubClient = hubClient;
        m_additionalHeaders = additionalHeaders;
    }

    /**
     * Request a multi-file upload of the items described in the given map.
     *
     * @param parentId ID of the workflow group to upload into
     * @param manifest upload manifest
     * @param eTag expected entity tag for the parent group, may be {@code null}
     * @return mapping from relative path to file upload instructions, or {@link Optional#empty()} if the
     *     parent workflow group has changed
     * @throws ResourceAccessException if the request failed
     */
    public Optional<UploadStarted> initiateUpload(final ItemID parentId,
            final UploadManifest manifest, final EntityTag eTag) throws ResourceAccessException {
        Map<String, String> additionalHeaders = new HashMap<>(m_additionalHeaders);
        if (eTag != null) {
            additionalHeaders.put(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(eTag));
        }

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.catalog()
                    .initiateUpload(parentId.id(), manifest, SLOW_OPERATION_READ_TIMEOUT, additionalHeaders);
            if (response.statusCode() == Status.PRECONDITION_FAILED.getStatusCode()) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(checkSuccessful(response).value());
            }
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        }
    }

    /**
     * Request an additional upload part for the upload with the given ID.
     *
     * @param uploadId upload ID
     * @param partNumber part number of the next part (must be one larger than the last requested one)
     * @return target of the new upload part
     * @throws ResourceAccessException if the request failed
     */
    public UploadTarget requestAdditionalUploadPart(final String uploadId, final int partNumber) // NOSONAR
            throws ResourceAccessException {
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.catalog().requestPartUpload(uploadId, partNumber, m_additionalHeaders);
            return checkSuccessful(response).value();
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        }
    }

    /**
     * Poll the current status of the upload with the given ID.
     *
     * @param uploadId upload ID
     * @return state of the upload
     * @throws ResourceAccessException if the request failed
     */
    public UploadStatus pollUploadState(final String uploadId) throws ResourceAccessException {
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.catalog().pollUploadStatus(uploadId, m_additionalHeaders);
            return checkSuccessful(response).value();
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        }
    }

    /**
     * Report that the upload with the given ID has been finished.
     *
     * @param uploadId upload ID
     * @param artifactETags mapping from upload part number to entity tag received when uploading
     * @throws ResourceAccessException if the request failed
     */
    public void reportUploadFinished(final String uploadId, final Map<Integer, EntityTag> artifactETags)
        throws ResourceAccessException {
        final Map<Integer, String> artifactETagMap = artifactETags.entrySet().stream() //
            .sorted(Comparator.comparingInt(Entry::getKey)) //
            .collect(Collectors.toMap(Entry::getKey, e -> ETAG_DELEGATE.toString(e.getValue()), (a, b) -> a,
                LinkedHashMap::new));

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.catalog().reportUploadFinished(uploadId, artifactETagMap,
                m_additionalHeaders);
            checkSuccessful(response);
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        }
    }

    /**
     * Request that the upload process is cancelled.
     *
     * @param uploadId upload ID
     * @throws ResourceAccessException if the request failed
     */
    public void cancelUpload(final String uploadId) throws ResourceAccessException {
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.catalog().cancelUpload(uploadId, m_additionalHeaders);
            checkSuccessful(response);
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        }
    }

    /**
     * Fetches a repository item from the catalog.
     *
     * @param itemIDOrPath either an item ID or a path to an item in the catalog
     * @param queryParams query parameters, may be {@code null}
     * @param version item version, may be {@code null}
     * @param ifNoneMatch entity tag for the {@code If-None-Match: <ETag>} header, may be null
     * @param ifMatch entity tag for the {@code If-Match: <ETag>} header, may be null
     * @return pair of fetched repository item and corresponding entity tag, or {@link Optional#empty()} if
     *     {@code ifNoneMatch} was non-{@code null} and the HTTP response was {@code 304 Not Modified} or
     *     {@code ifMatch} was non-{@code null} and the HTTP response was {@code 412 Precondition Failed}
     *
     * @throws ResourceAccessException if the request was not successful
     */
    public Optional<TaggedRepositoryItem> fetchRepositoryItem(final String itemIDOrPath,
            final Map<String, String> queryParams, final ItemVersion version, final EntityTag ifNoneMatch,
            final EntityTag ifMatch) throws ResourceAccessException {
        Map<String, String> additionalHeaders = new HashMap<>(m_additionalHeaders);
        additionalHeaders.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        if (ifNoneMatch != null) {
            additionalHeaders.put(HttpHeaders.IF_NONE_MATCH, ETAG_DELEGATE.toString(ifNoneMatch));
        }
        if (ifMatch != null) {
            additionalHeaders.put(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(ifMatch));
        }

        Map<String, String> nonNullQueryParams = new HashMap<>();
        if (queryParams != null) {
            nonNullQueryParams.putAll(queryParams);
        }

        final var detailsParam = nonNullQueryParams.get("details");
        final var deepParam = Boolean.valueOf(nonNullQueryParams.get("deep"));
        final var spaceDetailsParam = Boolean.valueOf(nonNullQueryParams.get("spaceDetails"));
        final var contribSpacesParam = nonNullQueryParams.get("contribSpaces");

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = itemIDOrPath.startsWith("*") ?
                    m_hubClient.catalog().getRepositoryItemMetaData(itemIDOrPath, detailsParam, deepParam,
                        spaceDetailsParam, contribSpacesParam, version, additionalHeaders) :
                    m_hubClient.catalog().getRepositoryItemByPath(new Path(itemIDOrPath), detailsParam, deepParam,
                            spaceDetailsParam, contribSpacesParam, version, additionalHeaders);
            if ((ifNoneMatch != null && response.statusCode() == Status.NOT_MODIFIED.getStatusCode()) ||
                    (ifMatch != null && response.statusCode() == Status.PRECONDITION_FAILED.getStatusCode())) {
                return Optional.empty();
            }
            final var item = checkSuccessful(response).value();
            return Optional.of(new TaggedRepositoryItem(item, response.etag().orElse(null)));
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        }
    }

    /**
     * Downloads an item from the repository.
     *
     * @param <R> type of the result value
     * @param id the id of the item which should be downloaded
     * @param itemType the type of the item which should be downloaded
     * @param contentHandler callback consuming the response data
     * @return value returned by the callback
     *
     * @throws IOException if an I/O error occurred while downloading
     * @throws CancelationException if the operation was canceled
     */
    public <R> R downloadItem(final ItemID id, final RepositoryItemType itemType,
            final DownloadContentHandler<R> contentHandler) throws IOException, CancelationException {
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            MediaType accept = MediaType.WILDCARD_TYPE;
            if (RepositoryItemType.WORKFLOW_GROUP == itemType) {
                accept = KNIME_WORKFLOW_GROUP_MEDIA_TYPE;
            } else if (RepositoryItemType.WORKFLOW == itemType || RepositoryItemType.COMPONENT == itemType) {
                accept = KNIME_WORKFLOW_MEDIA_TYPE;
            }
            final var response =
                m_hubClient.catalog().downloadItemById(id.id(), null, accept, contentHandler, m_additionalHeaders);
            return checkSuccessful(response).value();
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        }
    }

    /**
     * Checks that the given response signals success (via a 4XX HTTP status code).
     * @param <R>
     *
     * @param response {@link ApiResponse} to check
     * @return {@link Success}
     * @throws ResourceAccessException if the request was unsuccessful
     */
    static <R> Success<R> checkSuccessful(final ApiResponse<R> response) throws ResourceAccessException {
        final var result = response.result();
        if (!result.successful()) {
            final var failure = (Result.Failure<R>)result;
            throw HttpExceptionUtils.wrapException(response.statusCode(), failure.message());
        }
        return (Result.Success<R>) result;
    }

    /**
     * Returns the base URI of the associated {@link ApiClient}.
     *
     * @return hub base URI
     */
    public URI getHubAPIBaseURI() {
        return m_hubClient.getApiClient().getBaseURI();
    }

}
