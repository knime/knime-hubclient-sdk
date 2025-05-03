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
import java.net.URL;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IPath;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.ent.DownloadStatus;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.ent.UploadStarted;
import org.knime.hub.client.sdk.ent.UploadStatus;

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
final class CatalogServiceUtils {

    private static final HeaderDelegate<EntityTag> ETAG_DELEGATE =
        RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);

    /** Read timeout for expensive operations like {@link #initiateUpload(ItemID, UploadManifest, EntityTag)}. */
    private static final Duration SLOW_OPERATION_READ_TIMEOUT = Duration.ofMinutes(15);

    /** Timeout for download status polls */
    static final Duration DOWNLOAD_STATUS_POLL_TIMEOUT = Duration.ofSeconds(5);

    private static final String KNIME_SERVER_NAMESPACE = "knime";

    /** Maximum number of pre-fetched upload URLs per upload. */
    static final int MAX_NUM_PREFETCHED_UPLOAD_PARTS = 500;

    /** Relation that points to the endpoint for initiating an async upload flow. */
    static final String INITIATE_UPLOAD = "%s:initiate-upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that points to the endpoint for initiating an artifact download. */
    static final String INITIATE_DOWNLOAD = "%s:download-artifact".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items download control. */
    static final String DOWNLOAD = "%s:download".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items upload control. */
    static final String UPLOAD = "%s:upload".formatted(KNIME_SERVER_NAMESPACE);

    /** Relation that provides the items edit control. */
    static final String EDIT = "edit";

    /** Media type for a KNIME Workflow. */
    static final MediaType KNIME_WORKFLOW_TYPE = new MediaType("application", "vnd.knime.workflow");

    /** Media type for a KNIME Workflow (KNWF). */
    static final MediaType KNIME_WORKFLOW_TYPE_ZIP = new MediaType("application", "vnd.knime.workflow+zip");

    /** Media type for a KNIME Workflow Group. */
    static final MediaType KNIME_WORKFLOW_GROUP_TYPE = new MediaType("application", "vnd.knime.workflow-group");

    /** Media type for a zipped KNIME Workflow Group (KNAR). */
    static final MediaType KNIME_WORKFLOW_GROUP_TYPE_ZIP = new MediaType("application", "vnd.knime.workflow-group+zip");

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

    private CatalogServiceUtils() {
    }

    /**
     * Request a multi-file upload of the items described in the given map.
     *
     * @param parentId ID of the workflow group to upload into
     * @param manifest upload manifest
     * @param eTag expected entity tag for the parent group, may be {@code null}
     * @return mapping from relative path to file upload instructions, or {@link Optional#empty()} if the parent
     *         workflow group has changed
     * @throws IOException if an I/O error occurred
     * @throws CouldNotAuthorizeException if the request could not be authorized
     */
    static Optional<UploadStarted> initiateUpload(final CatalogServiceClient catalogClient,
        final Map<String, String> additionalHeaders, final ItemID parentId, final UploadManifest manifest,
        final EntityTag eTag) throws IOException, CouldNotAuthorizeException {

        final Map<String, String> headers = new HashMap<>(additionalHeaders);
        if (eTag != null) {
            headers.put(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(eTag));
        }

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response =
                catalogClient.initiateUpload(parentId.id(), manifest, SLOW_OPERATION_READ_TIMEOUT, headers);
            if (response.statusCode() == Status.PRECONDITION_FAILED.getStatusCode()) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(response.checkSuccessful());
            }
        }
    }

    /**
     * Report that the upload with the given ID has been finished.
     *
     * @param uploadId upload ID
     * @param artifactETags mapping from upload part number to entity tag received when uploading
     * @throws IOException if an I/O error occurred
     * @throws CouldNotAuthorizeException if the request could not be authorized
     */
    static void reportUploadFinished(final CatalogServiceClient catalogClient,
        final Map<String, String> additionalHeaders, final String uploadId, final Map<Integer, EntityTag> artifactETags)
        throws IOException, CouldNotAuthorizeException {
        final Map<Integer, String> artifactETagMap = artifactETags.entrySet().stream() //
            .sorted(Comparator.comparingInt(Entry::getKey)) //
            .collect(Collectors.toMap(Entry::getKey, e -> ETAG_DELEGATE.toString(e.getValue()), (a, b) -> a,
                LinkedHashMap::new));

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = catalogClient.reportUploadFinished(uploadId, artifactETagMap, additionalHeaders);
            response.checkSuccessful();
        }
    }

    /**
     * Request that the upload process is cancelled.
     *
     * @param uploadId upload ID
     * @throws IOException if an I/O error occurred
     * @throws CouldNotAuthorizeException if the request could not be authorized
     */
    static void cancelUpload(final CatalogServiceClient catalogClient, final Map<String, String> additionalHeaders,
        final String uploadId) throws IOException, CouldNotAuthorizeException {
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = catalogClient.cancelUpload(uploadId, additionalHeaders);
            response.checkSuccessful();
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
     *         {@code ifNoneMatch} was non-{@code null} and the HTTP response was {@code 304 Not Modified} or
     *         {@code ifMatch} was non-{@code null} and the HTTP response was {@code 412 Precondition Failed}
     * @throws IOException if an I/O error occurred
     * @throws CouldNotAuthorizeException if the request could not be authorized
     */
    static Optional<TaggedRepositoryItem> fetchRepositoryItem(final CatalogServiceClient catalogClient,
        final Map<String, String> additionalHeaders, final String itemIDOrPath, final Map<String, String> queryParams,
        final ItemVersion version, final EntityTag ifNoneMatch, final EntityTag ifMatch)
        throws IOException, CouldNotAuthorizeException {

        Map<String, String> headers = new HashMap<>(additionalHeaders);
        headers.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        if (ifNoneMatch != null) {
            headers.put(HttpHeaders.IF_NONE_MATCH, ETAG_DELEGATE.toString(ifNoneMatch));
        }
        if (ifMatch != null) {
            headers.put(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(ifMatch));
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
            final var response = itemIDOrPath.startsWith("*")
                ? catalogClient.getRepositoryItemById(itemIDOrPath, detailsParam, deepParam, spaceDetailsParam,
                    contribSpacesParam, version, headers)
                : catalogClient.getRepositoryItemByPath(IPath.forPosix(itemIDOrPath), detailsParam, deepParam,
                    spaceDetailsParam, contribSpacesParam, version, headers);
            if ((ifNoneMatch != null && response.statusCode() == Status.NOT_MODIFIED.getStatusCode())
                || (ifMatch != null && response.statusCode() == Status.PRECONDITION_FAILED.getStatusCode())) {
                return Optional.empty();
            }
            return Optional.of(new TaggedRepositoryItem(response.checkSuccessful(), response.etag().orElse(null)));
        }
    }

    static URL awaitDownloadReady(final CatalogServiceClient catalogClient,
        final Map<String, String> additionalHeaders, final String downloadId,
        final Consumer<DownloadStatus> pollCallback)
        throws IOException, CouldNotAuthorizeException, InterruptedException {

        final Set<DownloadStatus.StatusEnum> endStates = EnumSet.of(DownloadStatus.StatusEnum.READY,
            DownloadStatus.StatusEnum.ABORTED, DownloadStatus.StatusEnum.FAILED);

        final var finalState = poll(-1, new PollingCallable<DownloadStatus>() {
            @Override
            public ApiResponse<DownloadStatus> poll() throws IOException, CouldNotAuthorizeException {
                return catalogClient.pollDownloadStatus(downloadId, additionalHeaders);
            }

            @Override
            public boolean accept(final DownloadStatus state) {
                pollCallback.accept(state);
                return endStates.contains(state.getStatus());
            }
        });

        return switch (finalState.getStatus()) {
            case ABORTED, FAILED -> throw new IOException(finalState.getStatusMessage());
            case PREPARING, ZIPPING -> throw new IllegalStateException(
                "Stopped polling download early even though no timeout was provided");
            case READY -> finalState.getDownloadUrl().orElseThrow();
        };
    }

    static UploadStatus awaitUploadProcessed(final CatalogServiceClient catalogClient,
        final Map<String, String> clientHeaders, final String uploadId, final long timeoutMillis,
        final Consumer<UploadStatus> pollCallback)
        throws IOException, CouldNotAuthorizeException, InterruptedException {

        final Set<UploadStatus.StatusEnum> endStates = EnumSet.of(UploadStatus.StatusEnum.COMPLETED,
            UploadStatus.StatusEnum.ABORTED, UploadStatus.StatusEnum.FAILED);

        return poll(timeoutMillis, new PollingCallable<UploadStatus>() {
            @Override
            public ApiResponse<UploadStatus> poll() throws IOException, CouldNotAuthorizeException {
                return catalogClient.pollUploadStatus(uploadId, clientHeaders);
            }

            @Override
            public boolean accept(final UploadStatus state) {
                pollCallback.accept(state);
                return endStates.contains(state.getStatus());
            }
        });
    }

    private interface PollingCallable<T> {

        ApiResponse<T> poll() throws IOException, CouldNotAuthorizeException;

        boolean accept(T result);
    }

    private static <T> T poll(final long timeoutMillis, final PollingCallable<T> callable)
            throws IOException, CouldNotAuthorizeException, InterruptedException {

        final var t0 = System.currentTimeMillis();
        for (var numberOfStatusPolls = 0L;; numberOfStatusPolls++) {
            final T state;
            try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                state = callable.poll().checkSuccessful();
            }

            if (callable.accept(state)) {
                return state;
            }

            Thread.sleep(sleepTime(numberOfStatusPolls));

            final long elapsed = System.currentTimeMillis() - t0;
            if (timeoutMillis >= 0 && elapsed > timeoutMillis) {
                return state;
            }
        }
    }

    private static long sleepTime(final long numberOfStatusPolls) {
        // Sequence: 200ms, 400ms, 600ms, 800ms and then 1s until the timeout is reached
        return numberOfStatusPolls < 4 ? (200 * (numberOfStatusPolls + 1)) : 1_000;
    }
}
