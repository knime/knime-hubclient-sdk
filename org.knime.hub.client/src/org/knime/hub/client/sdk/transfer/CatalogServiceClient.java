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
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.jaxrs.client.ClientProperties;
import org.eclipse.core.runtime.IPath;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.Authenticator;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.core.util.hub.HubItemVersion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.knime.enterprise.server.mason.MasonUtil;
import com.knime.enterprise.server.mason.Relation;
import com.knime.enterprise.server.rest.api.v4.repository.ent.RepositoryItem;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;

/**
  * Wrapper for all REST endpoint calls needed for catalog operations.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public class CatalogServiceClient extends AbstractHubServiceClient {
    /** Read timeout for expensive operations like {@link #initiateUpload(ItemID, UploadManifest, EntityTag)}. */
    private static final Duration SLOW_OPERATION_READ_TIMEOUT = Duration.ofMinutes(15);

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

    /**
     * Callback interface for endpoints returning larger binary results.
     * @param <R> type of the result value
     */
    @FunctionalInterface
    public interface DownloadContentHandler<R> {
        /**
         * Called if the request succeeded, may consume the input stream and return a result, which is passed out of the
         * method doing the request. The input stream is closed after this method returns.
         *
         * @param data response data as input stream
         * @param contentLength content length if available
         * @return result value
         * @throws IOException
         * @throws CanceledExecutionException
         */
        R handleDownload(InputStream data, OptionalLong contentLength) // NOSONAR `OptionalLong` is fine
                throws IOException, CanceledExecutionException;
    }

    private static final String VERSION_QUERY_PARAM = "version";

    /**
     * Manifest describing which items are intended to be uploaded.
     *
     * @param items mapping from relative path inside the group to description of the item to upload
     */
    @JsonSerialize
    public record UploadManifest(Map<IPath, ItemUploadRequest> items) {}

    /**
     * Request to upload a single file.
     *
     * @param itemContentType content type of the item
     * @param initialPartCount number of multi-part upload parts to request
     */
    @JsonSerialize
    public record ItemUploadRequest(String itemContentType, int initialPartCount) {}

    /**
     * Response containing instructions for how to upload the items contained in the {@link UploadManifest}.
     *
     * @param items mapping from relative path inside the group to upload instructions
     */
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadStarted(Map<String, ItemUploadInstructions> items) {}

    /**
     * Instructions for uploading one item.
     *
     * @param uploadId ID of the initiated upload
     * @param parts mapping from part number to upload target for all requested upload parts (may be absent)
     */
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemUploadInstructions(String uploadId, Optional<Map<Integer, UploadTarget>> parts) {}

    /**
     * Target description for a file part HTTP upload.
     *
     * @param method HTTP method to use
     * @param url request URL
     * @param header request headers to send
     */
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadTarget(String method, URL url, Map<String, List<String>> header) {}

    /**
     * Current status of a single-file upload.
     *
     * @param uploadId ID of the upload
     * @param initiatorAccountId account ID of the user who initiated the upload
     * @param status status of the upload
     * @param statusMessage message describing the status
     * @param lastUpdated timestamp indicating when the status last changed
     * @param targetCanonicalPath canonical destination path in the Hub catalog
     */
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadStatus(String uploadId, String initiatorAccountId, UploadStatus.Status status,
            String statusMessage, Instant lastUpdated, String targetCanonicalPath) {

        /** Stages of the upload lifecycle. */
        public enum Status {
            /** Waiting for item upload to finish. */
            PREPARED,
            /** Item has been uploaded and is currently being analyzed. */
            ANALYSIS_PENDING,
            /** Upload has been completed successfully. */
            COMPLETED,
            /** Upload has failed. */
            FAILED,
            /** Upload has been aborted by the user. */
            ABORTED
        }
    }

    /**
     * Target description for a file HTTP download.
     *
     * @param name item name
     * @param type item type
     * @param url download URL
     */
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DownloadTarget(String name, RepositoryItem.Type type, URL url) {}

    private final Duration m_readTimeout;

    /**
     * @param hubBaseUrl base URL of the Hub API (not including {@code /knime/rest})
     * @param authenticator Hub authenticator
     * @param connectTimeout connect timeout for connections
     * @param readTimeout read timeout for connections
     */
    public CatalogServiceClient(final String hubBaseUrl, final Authenticator authenticator,
            final Duration connectTimeout, final Duration readTimeout) {
        super(createClientBuilder(connectTimeout, readTimeout), hubBaseUrl, authenticator);
        m_readTimeout = readTimeout;
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
        m_logger.debug(() -> "Initiating upload of %d items".formatted(manifest.items().size()));
        final var uriBuilder = UriBuilder.fromUri(m_hubBaseUrl).segment("repository", parentId.id(), "manifest");

        // this call can be very slow, and the user can abort at any point in time
        final WebTarget target = m_client.target(uriBuilder) //
            .property(ClientProperties.HTTP_RECEIVE_TIMEOUT_PROP,
                Math.max(SLOW_OPERATION_READ_TIMEOUT.toMillis(), m_readTimeout.toMillis()));

        final var request = authorizedRequest(target, true);
        if (eTag != null) {
            request.header(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(eTag));
        }

        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var response = request.post(Entity.json(manifest))) {
            if (response.getStatus() == Status.PRECONDITION_FAILED.getStatusCode()) {
                return Optional.empty();
            } else {
                checkSuccessful(response);
                return Optional.of(response.readEntity(UploadStarted.class));
            }
        } finally {
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
        final var uriBuilder = UriBuilder.fromUri(m_hubBaseUrl) //
            .segment("uploads", uploadId, "parts") //
            .queryParam("partNumber", Integer.toString(partNumber));
        final var request = authorizedRequest(m_client.target(uriBuilder), true) //
            .accept(MediaType.APPLICATION_JSON_TYPE);

        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var response = request.post(null)) {
            checkSuccessful(response);
            return response.readEntity(UploadTarget.class);
        } finally {
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
        final var uriBuilder = UriBuilder.fromUri(m_hubBaseUrl).segment("uploads", uploadId, "status");

        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var response = authorizedRequest(m_client.target(uriBuilder), true).get()) {
            checkSuccessful(response);
            return response.readEntity(UploadStatus.class);
        } finally {
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
        final var uriBuilder = UriBuilder.fromUri(m_hubBaseUrl).segment("uploads", uploadId);
        final var request = authorizedRequest(m_client.target(uriBuilder), true);
        final var t0 = System.currentTimeMillis();

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var response = request.post(Entity.json(artifactETagMap))) {
            checkSuccessful(response);
        } finally {
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
        final var uriBuilder = UriBuilder.fromUri(m_hubBaseUrl).segment("uploads", uploadId);

        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var response = authorizedRequest(m_client.target(uriBuilder), true).delete()) {
            checkSuccessful(response);
        } finally {
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
    public Optional<Pair<RepositoryItem, EntityTag>> fetchRepositoryItem(final String itemIdOrPath,
            final Map<String, String> queryParams, final HubItemVersion version, final EntityTag ifNoneMatch,
            final EntityTag ifMatch) throws ResourceAccessException {
        final var uriBuilder = UriBuilder.fromUri(m_hubBaseUrl).segment("repository").path(itemIdOrPath);
        if (version != null) {
            uriBuilder.queryParam(VERSION_QUERY_PARAM, version.getQueryParameterValue());
        }

        if (queryParams != null) {
            queryParams.forEach(uriBuilder::queryParam);
        }

        final var request = authorizedRequest(m_client.target(uriBuilder), false) //
            .accept(MasonUtil.MEDIATYPE_APPLICATION_MASON);
        if (ifNoneMatch != null) {
            request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }

        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var response = request.get()) {
            if ((ifNoneMatch != null && response.getStatus() == Status.NOT_MODIFIED.getStatusCode())
                    || (ifMatch != null && response.getStatus() == Status.PRECONDITION_FAILED.getStatusCode())) {
                return Optional.empty();
            }
            checkSuccessful(response);
            return Optional.of(Pair.of(response.readEntity(RepositoryItem.class), response.getEntityTag()));
        } finally {
            m_logger.debug(() -> "Request '%s' took %.3fs".formatted(uriBuilder.toTemplate(),
                (System.currentTimeMillis() - t0) / 1000.0));
        }
    }

    /**
     * Downloads an item from the repository.
     *
     * @param <R> type of the result value
     * @param method HTTP method to use
     * @param url URL to request
     * @param contentHandler callback consuming the response data
     * @return value returned by the callback
     * @throws IOException
     * @throws CanceledExecutionException
     */
    public <R> R downloadItem(final String method, final URI url, final DownloadContentHandler<R> contentHandler)
            throws IOException, CanceledExecutionException {
        final var t0 = System.currentTimeMillis();
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var response = authorizedRequest(m_client.target(url), true).method(method)) {
            checkSuccessful(response);
            final var length = response.getLength();
            try (final var inStream = response.readEntity(InputStream.class)) {
                return contentHandler.handleDownload(inStream,
                    length < 0 ? OptionalLong.empty() : OptionalLong.of(length));
            } finally {
                m_logger.debug(() -> "Request '%s' took %.3fs".formatted(url,
                    (System.currentTimeMillis() - t0) / 1000.0));
            }
        }
    }
}
