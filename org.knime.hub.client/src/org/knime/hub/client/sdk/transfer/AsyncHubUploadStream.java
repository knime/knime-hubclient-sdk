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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.ent.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.ItemUploadRequest;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.knime.hub.client.sdk.transfer.HubUploader.UploadPartSupplier;
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
    private static final int MAX_CHUNK_SIZE = 8 * (int)FileUtils.ONE_MB;

    private final @NotOwning CatalogServiceClient m_hubClient;
    private final Map<String, String> m_clientHeaders;

    private final String m_itemName;
    private final String m_uploadId;
    private final Duration m_timeout;

    private final FilePartUploader m_filePartUploader;
    private final Map<Integer, EntityTag> m_finishedParts = new LinkedHashMap<>();

    private @Owning ChunkingByteOutputStream m_chunkingOutputStream;
    private Future<Pair<Integer, EntityTag>> m_pendingUpload;
    private volatile boolean m_canceled;

    private AsyncHubUploadStream(final AsyncUploadStreamBuilder builder) {
        m_hubClient = builder.m_hubClient;
        m_clientHeaders = builder.m_additionalHeaders;
        m_itemName = builder.m_itemName;
        m_uploadId = builder.m_uploadInstructions.getUploadId();
        m_timeout = builder.m_timeout;

        // Create file part uploader to upload separate chunks of data
        m_filePartUploader =
            new FilePartUploader(URLConnectionUploader.getInstance(), NUMBER_OF_PART_UPLOAD_RETRIES, false);

        // Create the supplier which can request additional upload parts
        final var partSupplier = new UploadPartSupplier(m_hubClient, m_clientHeaders, builder.m_uploadInstructions);
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

    /**
     * Creates a new {@link AsyncUploadStreamBuilder}
     *
     * @return {@link AsyncUploadStreamBuilder}
     */
    public static AsyncUploadStreamBuilder builder() {
        return new AsyncUploadStreamBuilder();
    }

    /**
     * Asynchronous upload stream builder
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    public static final class AsyncUploadStreamBuilder {

        private @NotOwning CatalogServiceClient m_hubClient;
        private Map<String, String> m_additionalHeaders = new HashMap<>();

        private String m_itemName;
        private boolean m_isWorkflowLike;
        private String m_parentId;
        private EntityTag m_parentETag;

        private ItemUploadInstructions m_uploadInstructions;
        private Duration m_timeout = Duration.ofMillis(Long.MAX_VALUE);

        private AsyncUploadStreamBuilder() {
        }

        /**
         * Adds the {@link CatalogServiceClient}, can't be null
         *
         * @param catalogClient {@link CatalogServiceClient}
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder withCatalogClient(final CatalogServiceClient catalogClient) {
            m_hubClient = catalogClient;
            return this;
        }

        /**
         * Adds the ID of the parent group, can't be null
         *
         * @param id the ID of the parent group
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder withParentId(final String id) {
            m_parentId = id;
            return this;
        }

        /**
         * Adds the {@link EntityTag} of the parent group, can be null
         *
         * @param eTag {@link EntityTag}
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder withParentETag(final EntityTag eTag) {
            m_parentETag = eTag;
            return this;
        }

        /**
         * Adds the name of the item which is uploaded, can't be null
         *
         * @param itemName the name of the item which is uploaded
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder withItemName(final String itemName) {
            m_itemName = itemName;
            return this;
        }

        /**
         * Determines if the uploaded item is either workflow like (workflow, component) or not (data files).
         * Workflow groups are not supported.
         *
         * @param isWorkflowLike <code>true</code> if the uploaded item is a workflow or component
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder isWorkflowLike(final boolean isWorkflowLike) {
            m_isWorkflowLike = isWorkflowLike;
            return this;
        }

        /**
         * Adds a timeout to the upload process
         *
         * @param timeout {@link Duration}
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder withTimeout(final Duration timeout) {
            if (timeout != null) {
                m_timeout = timeout;
            }
            return this;
        }

        /**
         * Adds additional headers to the requests of the upload process
         *
         * @param headerMap the map of headers
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder withHeaders(final Map<String, String> headerMap) {
            if (headerMap != null) {
                m_additionalHeaders.putAll(headerMap);
            }
            return this;
        }

        /**
         * Creates an asynchronous upload stream to the hub.
         *
         * @return an {@link AsyncHubUploadStream} or null if the precondition check with the parentEtag failed
         *
         * @throws IOException if an I/O error occurred during the upload
         * @throws CouldNotAuthorizeException if the authenticator has lost connection
         */
        public @Owning AsyncHubUploadStream build() throws IOException, CouldNotAuthorizeException {
            CheckUtils.checkArgumentNotNull(m_hubClient);
            CheckUtils.checkArgumentNotNull(m_parentId);
            CheckUtils.checkArgumentNotNull(m_itemName);

            final var mediaType = m_isWorkflowLike ? CatalogServiceUtils.KNIME_WORKFLOW_TYPE_ZIP.toString()
                : MediaType.APPLICATION_OCTET_STREAM;
            final var uploadParts =
                Math.min(CatalogServiceUtils.MAX_NUM_PREFETCHED_UPLOAD_PARTS, NUMBER_OF_INITIAL_PARTS);
            // Initiate upload request
            final var manifest = new UploadManifest(Map.of(m_itemName, new ItemUploadRequest(mediaType, uploadParts)));
            final var preparedUploadOpt = CatalogServiceUtils.initiateUpload(m_hubClient, m_additionalHeaders,
                new ItemID(m_parentId), manifest, m_parentETag);
            if (preparedUploadOpt.isEmpty()) {
                return null;
            }

            // Obtain upload instructions and create supplier for additional upload parts
            final var itemInstructions = preparedUploadOpt.get().getItems();
            m_uploadInstructions = CheckUtils.checkNotNull(itemInstructions.get(m_itemName));
            return new AsyncHubUploadStream(this);
        }

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
            final Pair<Integer, EntityTag> finishedPart = HubUploader.awaitPartFinished(pending);
            finishedPart.accept(m_finishedParts::put);
        }
    }

    /**
     * Cancels the upload process.
     *
     * @throws IOException if an I/O error occurred during cancellation
     * @throws CouldNotAuthorizeException if the authenticator has lost connection
     */
    public void cancel() throws IOException, CouldNotAuthorizeException {
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
            CatalogServiceUtils.cancelUpload(m_hubClient, m_clientHeaders, m_uploadId);
        }
    }

    private IOException cancelAfter(final IOException ioe) throws IOException {
        try {
            cancel();
        } catch (final IOException | CouldNotAuthorizeException inner) {
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
            CatalogServiceUtils.reportUploadFinished(m_hubClient, m_clientHeaders, m_uploadId, m_finishedParts);

            // wait for the upload to be completed server-side
            final var finalState = CatalogServiceUtils.awaitUploadProcessed(m_hubClient, m_clientHeaders, m_uploadId,
                m_timeout.toMillis(), state -> {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.atDebug() //
                            .addArgument(m_itemName) //
                            .addArgument(state.getStatus()) //
                            .addArgument(state.getStatusMessage()) //
                            .setMessage("Polling state of uploaded item '{}': {}, '{}'") //
                            .log();
                    }
                });
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
            throw new IOException("Upload of '%s' has been interrupted while waiting for Hub".formatted(m_itemName));
        } catch (final CouldNotAuthorizeException ex) {
            throw new IOException("Lost connection to Hub while closing upload of '%s'".formatted(m_itemName), ex);
        }
    }
}
