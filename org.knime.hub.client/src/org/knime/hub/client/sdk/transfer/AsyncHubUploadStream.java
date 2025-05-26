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
 *   Mar 25, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.FailureType;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.Result.Failure;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.ent.ProblemDescription;
import org.knime.hub.client.sdk.ent.catalog.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.catalog.ItemUploadRequest;
import org.knime.hub.client.sdk.ent.catalog.UploadManifest;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.HubUploader.UploadPartSupplier;
import org.knime.hub.client.sdk.transfer.internal.URLConnectionUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;

/**
 * Provides an asynchronous output stream to a hub instance.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public final class AsyncHubUploadStream extends OutputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncHubUploadStream.class);

    private static final int NUMBER_OF_INITIAL_PARTS = 1;
    private static final int NUMBER_OF_PART_UPLOAD_RETRIES = 2;

    /** Minimum <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html">according to Amazon</a> */
    private static final int MIN_CHUNK_SIZE = 5 * (int)FileUtils.ONE_MB;

    private static final int MAX_PARALLEL_UPLOADS = 2;

    private final @NotOwning CatalogServiceClient m_hubClient;
    private final Map<String, String> m_clientHeaders;

    private final String m_itemName;
    private final String m_uploadId;

    private final FilePartUploader m_filePartUploader;
    private final SortedMap<Integer, String> m_finishedParts = new TreeMap<>();

    private @Owning OutputStream m_chunkingOutputStream;
    private final Deque<Future<Result<Pair<Integer, EntityTag>, FailureValue>>> m_pendingUploads = new ArrayDeque<>();

    private final AtomicBoolean m_cancelled = new AtomicBoolean();
    private final BranchingExecMonitor m_rootMonitor = new BranchingExecMonitor(m_cancelled::get, prog -> {});

    /**
     * Creates a new asynchronous upload stream. If a parent {@link EntityTag ETag} is given and the parent workflow
     * group has a different ETag now, this method returns {@code null}.
     *
     * @param catalogClient catalog service client
     * @param clientHeaders additional headers for the catalog service client
     * @param parentId ID of the parent group to upload to
     * @param parentETag ETag of the parent group, may be {@code null}
     * @param itemName name of the item to upload
     * @param isWorkflowLike flag indicating whether or not the item is a workflow or component
     * @param chunkSize Preferred chunk size, ignored if it is smaller than S3's minimum chunk size (currently 5MB)
     * @return upload stream, may only be {@code null} if a parent ETag was provided and the parent workflow group has
     *         changed
     * @throws HubFailureIOException if an error occurred
     * @throws UnsupportedOperationException if asynchronous uploads are not supported by the connected Hub
     */
    @SuppressWarnings("java:S2301") // boolean flag as parameter
    public static @Owning AsyncHubUploadStream create(final CatalogServiceClient catalogClient,
        final Map<String, String> clientHeaders, final String parentId, final EntityTag parentETag,
        final String itemName, final boolean isWorkflowLike, final int chunkSize) throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(catalogClient);
        CheckUtils.checkArgumentNotNull(parentId);
        CheckUtils.checkArgumentNotNull(itemName);

        final var mediaType = isWorkflowLike ? AbstractHubTransfer.KNIME_WORKFLOW_TYPE_ZIP.toString()
            : MediaType.APPLICATION_OCTET_STREAM;
        final var uploadParts =
            Math.min(HubUploader.MAX_NUM_PREFETCHED_UPLOAD_PARTS, NUMBER_OF_INITIAL_PARTS);
        // Initiate upload request
        final var manifest = new UploadManifest(Map.of(itemName, new ItemUploadRequest(mediaType, uploadParts)));
        final var preparedUploadOpt = HubUploader.initiateUpload(catalogClient, clientHeaders,
            new ItemID(parentId), manifest, parentETag) //
                .orElseThrow(HubFailureIOException::new);
        if (preparedUploadOpt.isEmpty()) {
            return null;
        }

        // Obtain upload instructions and create supplier for additional upload parts
        final var itemInstructions = preparedUploadOpt.get().getItems();
        final var uploadInstructions = CheckUtils.checkNotNull(itemInstructions.get(itemName));
        return new AsyncHubUploadStream(catalogClient, clientHeaders, itemName, uploadInstructions, chunkSize);
    }

    private AsyncHubUploadStream(final CatalogServiceClient hubClient, final Map<String, String> clientHeaders,
        final String itemName, final ItemUploadInstructions uploadInstructions, final int chunkSize) {
        m_hubClient = hubClient;
        m_clientHeaders = clientHeaders;
        m_itemName = itemName;
        m_uploadId = uploadInstructions.getUploadId();

        // Create file part uploader to upload separate chunks of data
        m_filePartUploader = new FilePartUploader(URLConnectionUploader.INSTANCE, NUMBER_OF_PART_UPLOAD_RETRIES, false);

        // Create the supplier which can request additional upload parts
        final var partSupplier = new UploadPartSupplier(m_hubClient, m_clientHeaders, uploadInstructions);

        // Create chunking output stream
        m_chunkingOutputStream = new ChunkingByteOutputStream(Math.max(chunkSize, MIN_CHUNK_SIZE), null) {
            @Override
            public void chunkFinished(final int chunkNumber, final byte[] chunk, final byte[] hash) throws IOException {
                if (m_pendingUploads.size() == MAX_PARALLEL_UPLOADS) {
                    awaitPartsFinished(1);
                }

                final var partNumber = chunkNumber + 1;
                m_pendingUploads.addLast(m_filePartUploader.uploadDataChunk(m_itemName, partNumber, partSupplier, chunk,
                    hash, m_rootMonitor.createLeafChild(null, 0.0)));
            }
        };
    }

    /**
     * Returns the number of bytes this stream has successfully transferred.
     *
     * @return number of bytes transferred
     */
    public long getBytesTransferred() {
        return m_rootMonitor.getBytesTransferred();
    }

    @Override
    public synchronized void write(final int b) throws IOException {
        try {
            m_chunkingOutputStream.write(b);
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    @Override
    public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
        try {
            m_chunkingOutputStream.write(b, off, len);
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    @Override
    public synchronized void write(final byte[] b) throws IOException {
        try {
            m_chunkingOutputStream.write(b);
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    private void awaitPartsFinished(final int reduceBy) throws IOException {
        try {
            for (var i = 0; i < reduceBy && !m_pendingUploads.isEmpty(); i++) {
                final var pending = m_pendingUploads.removeFirst();
                final var result = AbstractHubTransfer.waitForCancellable(pending, m_cancelled::get,
                    thrw -> Result.failure(FailureValue.fromUnexpectedThrowable( //
                        "Failed to upload item", List.of("Unexpected error while uploading item (%s): %s" //
                            .formatted(thrw.getClass().getSimpleName(), thrw.getMessage())),
                        thrw)));
                final var finishedPart = result.orElseThrow(HubFailureIOException::new);
                m_finishedParts.put(finishedPart.getKey(),
                    AbstractHubTransfer.ETAG_DELEGATE.toString(finishedPart.getValue()));
            }
        } catch (CancelationException ex) { // NOSONAR ignore because we've already been cancelled
        }
    }

    /**
     * Cancels the upload process, may be called asynchronously.
     *
     * @throws HubFailureIOException if an I/O error occurred while notifying Hub of the cancellation
     */
    public void cancel() throws HubFailureIOException {
        // set immediately to cancel uploads etc.
        m_cancelled.set(true);

        // critical section of synchronous clean-up operations:
        final Iterable<Future<?>> pending;
        synchronized (this) {
            if (m_chunkingOutputStream == null) {
                return;
            }


            // discard the in-memory chunking stream (so no more uploads are triggered) and redirect the input
            m_chunkingOutputStream = NullOutputStream.INSTANCE;

            // cancel already pending upload if necessary
            pending = List.copyOf(m_pendingUploads);
            m_pendingUploads.clear();
        }

        // operations can be performed asynchronously:

        // cancel the pending upload(s)
        pending.forEach(future -> future.cancel(true));

        // notify Hub of the cancellation
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.cancelUpload(m_uploadId, m_clientHeaders);
            if (response.result() instanceof Failure<?, ProblemDescription> failure) {
                final ProblemDescription problem = failure.failure();
                final var userText = Stream.concat(Stream.of(problem.getTitle()), problem.getDetails().stream()) //
                        .filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));
                LOGGER.atDebug().log("Hub request to cancel upload failed: {} {}", response.statusCode(), userText);
            }
        }
    }

    private HubFailureIOException cancelAfter(final IOException ioe) throws IOException {
        try {
            cancel();
        } catch (final IOException inner) {
            ioe.addSuppressed(inner);
        }
        throw ioe;
    }

    @Override
    public synchronized void flush() throws IOException {
        if (m_chunkingOutputStream == null) {
            return;
        }
        try {
            m_chunkingOutputStream.flush();
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    /**
     * Closes the stream and waits for Hub to finish processing the uploaded items. Calling {@link #close()} is
     * equivalent to calling {@code closeAndAwait(null, () -> false)}.
     *
     * @param timeout maximum time to wait for Hub to finish processing the upload, may be {@code null} to mean infinity
     * @param cancelChecker called to find out whether or not this method should be canceled ({@code true} -> cancel)
     * @throws CancelationException if the operation was canceled
     * @throws IOException if an error occurred
     */
    public synchronized void closeAndAwait(final Duration timeout, final BooleanSupplier cancelChecker)
        throws CancelationException, IOException {
        // close the chunking output stream, potentially starting a last chunk upload
        try (final var out = m_chunkingOutputStream) {
            if (out == null) {
                // already closed
                return;
            }
            m_chunkingOutputStream = null;
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }

        try {
            // await all pending part uploads
            awaitPartsFinished(MAX_PARALLEL_UPLOADS);
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }

        // report back to catalog
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = m_hubClient.reportUploadFinished(m_uploadId, m_finishedParts, m_clientHeaders);
            if (response.result() instanceof Failure<Void, ProblemDescription> failure) {
                final var problem = failure.failure();
                throw new HubFailureIOException(new FailureValue(FailureType.UPLOAD_FINISHED_CALL_FAILED,
                    response.statusCode(), response.headers(),
                    "Hub failed to acknowledge finished upload: " + problem.getTitle(), problem.getDetails(), null));
            }
        }

        // wait for the upload to be completed server-side
        final long timeoutMillis = timeout == null ? -1 : timeout.toMillis();
        final var finalState = HubUploader.awaitUploadProcessed(m_hubClient, m_clientHeaders, m_itemName,
            m_uploadId, timeoutMillis, cancelChecker).orElseThrow(HubFailureIOException::new);

        switch (finalState.getStatus()) {
            case ABORTED, FAILED:
                throw new IOException(finalState.getStatusMessage());
            case ANALYSIS_PENDING, PREPARED: // timeout
                throw new IOException("Hub didn't complete upload within %.1fs" //
                    .formatted(timeout == null ? Double.NaN : (timeoutMillis / 1000.0)));
            case COMPLETED:
                break;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try (final var out = m_chunkingOutputStream) {
            closeAndAwait(null, () -> false);
        } catch (final CancelationException ex) {
            // should never happen
            throw new IOException(ex);
        }
    }
}
