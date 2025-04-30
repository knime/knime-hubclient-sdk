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
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.ent.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.ItemUploadRequest;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.knime.hub.client.sdk.transfer.HubUploader.UploadPartSupplier;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;

/**
 * Provides an asynchronous output stream to a hub instance.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public final class AsyncHubUploadStream extends OutputStream {

    private static final int NUMBER_OF_INITIAL_PARTS = 1;
    private static final int NUMBER_OF_PART_UPLOAD_RETRIES = 2;
    private static final int MAX_CHUNK_SIZE = 8 * (int)FileUtils.ONE_MB;

    private final @NotOwning CatalogServiceClient m_hubClient;
    private final Map<String, String> m_clientHeaders;

    private final String m_itemName;
    private final String m_uploadId;
    private final Duration m_timeout;

    private final FilePartUploader m_filePartUploader;
    private final SortedMap<Integer, String> m_finishedParts = new TreeMap<>();

    private @Owning ChunkingByteOutputStream m_chunkingOutputStream;
    private Future<Result<Pair<Integer, EntityTag>, FailureValue>> m_pendingUpload;
    private volatile boolean m_canceled;

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
     * @param timeout duration of time after which the upload stream aborts the polling for the upload to finish on Hub
     * @return upload stream of {@code null}, which indicates that the parent workflow group has changed
     * @throws HubFailureIOException
     * @throws UnsupportedOperationException if asynchronous uploads are not supported by the connected Hub
     */
    public static @Owning AsyncHubUploadStream create(final CatalogServiceClient catalogClient,
        final Map<String, String> clientHeaders, final String parentId, final EntityTag parentETag,
        final String itemName, final boolean isWorkflowLike, final Duration timeout) throws HubFailureIOException {
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
        return new AsyncHubUploadStream(catalogClient, clientHeaders, itemName, uploadInstructions, timeout);
    }

    private AsyncHubUploadStream(final CatalogServiceClient hubClient, final Map<String, String> clientHeaders,
        final String itemName, final ItemUploadInstructions uploadInstructions, final Duration timeout) {
        m_hubClient = hubClient;
        m_clientHeaders = clientHeaders;
        m_itemName = itemName;
        m_uploadId = uploadInstructions.getUploadId();
        m_timeout = Objects.requireNonNullElseGet(timeout, () -> Duration.ofDays(365));

        // Create file part uploader to upload separate chunks of data
        m_filePartUploader = new FilePartUploader(URLConnectionUploader.INSTANCE, NUMBER_OF_PART_UPLOAD_RETRIES, false);

        // Create the supplier which can request additional upload parts
        final var partSupplier = new UploadPartSupplier(m_hubClient, m_clientHeaders, uploadInstructions);
        final var execMonitor = LeafExecMonitor.nullExecMonitor(() -> m_canceled);

        // Create chunking output stream
        m_chunkingOutputStream = new ChunkingByteOutputStream(MAX_CHUNK_SIZE, null) {
            @Override
            public void chunkFinished(final int chunkNumber, final byte[] chunk, final byte[] hash) throws IOException {
                if (m_pendingUpload != null) {
                    awaitPartFinished();
                }
                final var partNumber = chunkNumber + 1;
                m_pendingUpload =
                    m_filePartUploader.uploadDataChunk(m_itemName, partNumber, partSupplier, chunk, hash, execMonitor);
            }
        };
    }

    @Override
    public void write(final int b) throws IOException {
        try {
            m_chunkingOutputStream.write(b);
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        try {
            m_chunkingOutputStream.write(b, off, len);
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        try {
            m_chunkingOutputStream.write(b);
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    private void awaitPartFinished() throws IOException {
        final var pending = m_pendingUpload;
        if (pending != null) {
            m_pendingUpload = null;
            final var finishedPart = HubUploader.awaitPartFinished(pending) //
                .orElseThrow(HubFailureIOException::new);
            m_finishedParts.put(finishedPart.getKey(),
                AbstractHubTransfer.ETAG_DELEGATE.toString(finishedPart.getValue()));
        }
    }

    /**
     * Cancels the upload process.
     *
     * @throws HubFailureIOException if an I/O error occurred during cancellation
     */
    public void cancel() throws HubFailureIOException {
        if (m_chunkingOutputStream != null) {
            // discard the (in-memory) chunking stream, thereby closing the outer one without triggering an upload
            m_chunkingOutputStream = null;

            // cancel already pending upload if necessary
            m_canceled = true;
            final var pending = m_pendingUpload;
            if (pending != null) {
                m_pendingUpload = null;
                pending.cancel(true);
            }

            // notify Catalog
            try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                m_hubClient.cancelUpload(m_uploadId, m_clientHeaders) //
                    .result() //
                    .orElseThrow(HubFailureIOException::new);
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
    public void flush() throws IOException {
        try {
            m_chunkingOutputStream.flush();
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }
    }

    @Override
    public void close() throws IOException {
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
            // await that last upload
            awaitPartFinished();
        } catch (final IOException ioe) {
            throw cancelAfter(ioe);
        }

        try {
            // report back to catalog
            try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                m_hubClient.reportUploadFinished(m_uploadId, m_finishedParts, m_clientHeaders).result() //
                    .orElseThrow(HubFailureIOException::new);
            }

            // wait for the upload to be completed server-side
            final var finalState = HubUploader.awaitUploadProcessed(m_hubClient, m_clientHeaders, m_itemName,
                m_uploadId, m_timeout.toMillis()).orElseThrow(HubFailureIOException::new);

            switch (finalState.getStatus()) {
                case ABORTED, FAILED:
                    throw new IOException(finalState.getStatusMessage());
                case ANALYSIS_PENDING, PREPARED: // timeout
                    throw new IOException("Hub didn't complete upload within %.1fs" //
                        .formatted(m_timeout.toMillis() / 1000.0));
                case COMPLETED:
                    break;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
