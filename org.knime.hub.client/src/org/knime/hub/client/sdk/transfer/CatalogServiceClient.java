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

import static com.knime.enterprise.server.rest.api.KnimeRelations.KNIME_SERVER_NAMESPACE;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.core.util.hub.HubItemVersion;
import org.knime.hub.client.sdk.ApiClient.DownloadContentHandler;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.RepositoryItem.RepositoryItemType;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.ent.UploadStarted;
import org.knime.hub.client.sdk.ent.UploadStatus;
import org.knime.hub.client.sdk.ent.UploadTarget;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.knime.enterprise.server.mason.MasonUtil;
import com.knime.enterprise.server.mason.Relation;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;
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
    
    /** Maximum number of pre-fetched upload URLs per upload. */
    public static final int MAX_NUM_PREFETCHED_UPLOAD_PARTS = 500;

    /** Relation that points to the endpoint for initiating an async upload flow. */
    public static final Relation INITIATE_UPLOAD =  Relation.create(KNIME_SERVER_NAMESPACE, "initiate-upload");

    /** Relation that allows the requester to poll the status of an item upload. */
    public static final Relation UPLOAD_STATUS =  Relation.create(KNIME_SERVER_NAMESPACE, "upload-status");

    /** Relation that allows the requester to request an upload part. */
    public static final Relation CREATE_UPLOAD_PART =  Relation.create(KNIME_SERVER_NAMESPACE, "create-upload-part");

    /** Relation that allows the requester to notify Catalog Service that an item upload is completed. */
    public static final Relation COMPLETE_UPLOAD_PART =  Relation.create(KNIME_SERVER_NAMESPACE, "complete-upload");

    /** Relation that allows the requester to abort an item upload. */
    public static final Relation ABORT_UPLOAD_PART =  Relation.create(KNIME_SERVER_NAMESPACE, "abort-upload");
    
    private static final String COULD_NOT_AUTHORIZE = "Could not authorize Hub REST call: ";
    
    private static final MediaType KNIME_WORKFLOW_MEDIA_TYPE =
            new MediaType("application", "vnd.knime.workflow+zip");
    private static final MediaType KNIME_WORKFLOW_GROUP_MEDIA_TYPE =
            new MediaType("application", "vnd.knime.workflow-group+zip");
    
    private static final String REPOSITORY_PATH_PIECE = "repository";

    final URI m_hubBaseURI;
    
    final NodeLogger m_logger;

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

    private HubClientAPI m_hubClient;
    
    private Map<String, String> m_apHeaders;
    
    /**
     * @param apiClient Hub API client
     * @param authenticator Hub authenticator
     * @param connectTimeout connect timeout for connections
     * @param readTimeout read timeout for connections
     */
    public CatalogServiceClient(final HubClientAPI hubClient, final Map<String, String> apHeaders) {
        m_hubClient = hubClient;
        m_apHeaders = apHeaders;
        m_hubBaseURI = hubClient.getApiClient().getBaseURI();
        m_logger = NodeLogger.getLogger(getClass());
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
        m_logger.debug(() -> "Initiating upload of %d items".formatted(manifest.getItems().size()));
        
        Map<String, String> additionalHeaders = new HashMap<>(m_apHeaders);
        if (eTag != null) {
            additionalHeaders.put(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(eTag));
        }
        
        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.initiateUpload(parentId.id(), manifest, additionalHeaders);
            if (response.getStatusCode() == Status.PRECONDITION_FAILED.getStatusCode()) {
                return Optional.empty();
            } else {
                checkSuccessful(response);
                return response.getResult().toOptional();
            }
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        } finally {
            final var uriBuilder = UriBuilder.fromUri(m_hubBaseURI)
                    .segment(REPOSITORY_PATH_PIECE, parentId.id(), "manifest");
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(uriBuilder.toTemplate(), // NOSONAR
                (System.currentTimeMillis() - t0) / 1000.0));
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
        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.requestPartUpload(uploadId, partNumber, 
                    new HashMap<>(m_apHeaders));
            checkSuccessful(response);
            return response.getResult().toOptional().get();
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        } finally {
            final var uriBuilder = UriBuilder.fromUri(m_hubBaseURI) //
                    .segment("uploads", uploadId, "parts") //
            .queryParam("partNumber", Integer.toString(partNumber));
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(uriBuilder.toTemplate(),
                (System.currentTimeMillis() - t0) / 1000.0));
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
        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.pollUploadStatus(uploadId, new HashMap<>(m_apHeaders));
            checkSuccessful(response);
            return response.getResult().toOptional().get();
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        } finally {
            final var uriBuilder = UriBuilder.fromUri(m_hubBaseURI).segment("uploads", uploadId, "status");
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(uriBuilder.toTemplate(),
                (System.currentTimeMillis() - t0) / 1000.0));
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

        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.reportUploadFinished(uploadId, artifactETagMap, 
                    new HashMap<>(m_apHeaders));
            checkSuccessful(response);
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        } finally {
            final var uriBuilder = UriBuilder.fromUri(m_hubBaseURI).segment("uploads", uploadId);
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(uriBuilder.toTemplate(),
                (System.currentTimeMillis() - t0) / 1000.0));
        }
    }

    /**
     * Request that the upload process is cancelled.
     *
     * @param uploadId upload ID
     * @throws ResourceAccessException if the request failed
     */
    public void cancelUpload(final String uploadId) throws ResourceAccessException {

        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.cancelUpload(uploadId, new HashMap<>(m_apHeaders));
            checkSuccessful(response);
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        } finally {
            final var uriBuilder = UriBuilder.fromUri(m_hubBaseURI).segment("uploads", uploadId);
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(uriBuilder.toTemplate(),
                (System.currentTimeMillis() - t0) / 1000.0));
        }
    }

    /**
     * Fetches a repository item from the catalog.
     *
     * @param itemIdOrPath either an item ID or a path to an item in the catalog
     * @param queryParams query parameters, may be {@code null}
     * @param version item version, may be {@code null}
     * @param ifNoneMatch entity tag for the {@code If-None-Match: <ETag>} header, may be null
     * @param ifMatch entity tag for the {@code If-Match: <ETag>} header, may be null
     * @return pair of fetched repository item and corresponding entity tag, or {@link Optional#empty()} if
     *     {@code ifNoneMatch} was non-{@code null} and the HTTP response was {@code 304 Not Modified} or
     *     {@code ifMatch} was non-{@code null} and the HTTP response was {@code 412 Precondition Failed}
     * @throws ResourceAccessException
     */
    public Optional<Pair<RepositoryItem, EntityTag>> fetchRepositoryItem(final String itemIDOrPath,
            final Map<String, String> queryParams, final HubItemVersion version, final EntityTag ifNoneMatch,
            final EntityTag ifMatch) throws ResourceAccessException {
        Map<String, String> additionalHeaders = new HashMap<>(m_apHeaders);
        additionalHeaders.put(HttpHeaders.ACCEPT, MasonUtil.MEDIATYPE_APPLICATION_MASON);
        if (ifNoneMatch != null) {
            additionalHeaders.put(HttpHeaders.IF_NONE_MATCH, ETAG_DELEGATE.toString(ifNoneMatch));
        }
        if (ifMatch != null) {
            additionalHeaders.put(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(ifMatch));
        }

        Map<String, String> nonNullQueryParams = new HashMap<>();
        if (queryParams != null) {
            nonNullQueryParams = new HashMap<>(queryParams);
        }
        
        final var detailsParam = nonNullQueryParams.get("details");
        final var deepParam = Boolean.valueOf(nonNullQueryParams.get("deep"));
        final var spaceDetailsParam = Boolean.valueOf(nonNullQueryParams.get("spaceDetails"));
        final var contribSpacesParam = nonNullQueryParams.get("contribSpaces");
        String versionParam = null;
        if (version != null) {
            versionParam = version.getQueryParameterValue().orElse(null);
        }
        final var spaceVersionParam = nonNullQueryParams.get("spaceVersion");
        
        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = itemIDOrPath.startsWith("*") ? 
                    m_hubClient.getRepositoryItemMetaData(itemIDOrPath, detailsParam, deepParam, spaceDetailsParam, 
                            contribSpacesParam, versionParam, spaceVersionParam, additionalHeaders) : 
                    m_hubClient.getRepositoryItemByPath(new Path(itemIDOrPath), detailsParam, deepParam, 
                            spaceDetailsParam, contribSpacesParam, versionParam, spaceVersionParam, additionalHeaders);
            if ((ifNoneMatch != null && response.getStatusCode() == Status.NOT_MODIFIED.getStatusCode())
                    || (ifMatch != null && response.getStatusCode() == Status.PRECONDITION_FAILED.getStatusCode())) {
                return Optional.empty();
            }
            checkSuccessful(response);
            return Optional.of(Pair.of(response.getResult().toOptional().get(), response.getETag().get()));
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        } finally {
            final var uriBuilder = UriBuilder.fromUri(m_hubBaseURI)
                    .segment(REPOSITORY_PATH_PIECE).path(itemIDOrPath);
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(uriBuilder.toTemplate(),
                (System.currentTimeMillis() - t0) / 1000.0));
        }
    }

    /**
     * Downloads an item from the repository.
     *
     * @param <R> type of the result value
     * @param path the path of the item which should be downloaded
     * @param contentHandler callback consuming the response data
     * @return value returned by the callback
     * @throws IOException
     * @throws CanceledExecutionException
     */
    public <R> R downloadItem(final IPath path, final RepositoryItemType itemType, 
            final DownloadContentHandler<R> contentHandler) throws IOException, CanceledExecutionException {
        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            MediaType accept = MediaType.WILDCARD_TYPE;
            if (RepositoryItemType.WORKFLOW_GROUP == itemType) {
                accept = KNIME_WORKFLOW_GROUP_MEDIA_TYPE;
            } else if (RepositoryItemType.WORKFLOW == itemType || RepositoryItemType.COMPONENT == itemType) {
                accept = KNIME_WORKFLOW_MEDIA_TYPE;
            }
            final var response = m_hubClient.downloadItemByPath(path, null,
                    null, accept, contentHandler, new HashMap<>(m_apHeaders));
            checkSuccessful(response);
            return response.getResult().toOptional().get();
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException(COULD_NOT_AUTHORIZE + e.getMessage(), e);
        } finally {
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(path, (System.currentTimeMillis() - t0) / 1000.0));
        }
    }
    
    /**
     * @return the Hub API's base URI (e.g. {@code "https://api.hub.knime.com"})
     */
    public URI getHubAPIBaseURI() {
        return m_hubBaseURI;
    }
    
    /**
     * Checks that the given response signals success (via a 4XX HTTP status code).
     *
     * @param response response to check
     * @throws ResourceAccessException if the request was unsuccessful
     */
    static void checkSuccessful(final ApiResponse<?> response) throws ResourceAccessException {
        final var result = response.getResult();
        if (!result.successful()) {
            final var failure = (Result.Failure<?>)result;
            throw HttpExceptionUtils.wrapException(response.getStatusCode(), failure.message());
        }
    }
    
}
