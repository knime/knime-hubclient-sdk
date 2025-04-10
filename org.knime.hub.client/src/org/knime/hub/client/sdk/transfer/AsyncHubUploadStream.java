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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.hub.client.sdk.api.CatalogClient;
import org.knime.hub.client.sdk.ent.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.ItemUploadRequest;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.knime.hub.client.sdk.transfer.HubUploader.UploadPartSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;

/**
 * Provides the asynchronous output stream to a hub instance.
 *
 * This class wraps the {@link AsyncUploadStreamBuilder} to initiate the upload process
 * and also build an {@link AsyncUploadStream} to write to.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public final class AsyncHubUploadStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncHubUploadStream.class);
    private static final int NUMBER_OF_INITIAL_PARTS = 1;

    private CatalogServiceClient m_catalogClient;
    private @NotOwning CatalogClient m_hubClient;
    private Map<String, String> m_additionalHeaders = new HashMap<>();

    private String m_itemName;
    private String m_uploadId;
    private ItemUploadInstructions m_uploadInstructions;
    private Duration m_timeout = Duration.ofMillis(Long.MAX_VALUE);

    /**
     * Constructor
     */
    public AsyncHubUploadStream() {
        // wrapper class for builder and stream
    }

    /**
     * Creates a new {@link AsyncUploadStreamBuilder}
     *
     * @return {@link AsyncUploadStreamBuilder}
     */
    public AsyncUploadStreamBuilder createAsyncUploadStream() {
        return new AsyncUploadStreamBuilder();
    }

    /**
     * Asynchronous upload stream builder
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    public final class AsyncUploadStreamBuilder {

        private String m_parentId;
        private EntityTag m_parentETag;
        private boolean m_isWorkflowLike;

        private AsyncUploadStreamBuilder() {
        }

        /**
         * Adds the {@link CatalogClient}, can't be null
         *
         * @param catalogClient {@link CatalogClient}
         * @return {@link AsyncUploadStreamBuilder}
         */
        public AsyncUploadStreamBuilder withCatalogClient(@NotOwning final CatalogClient catalogClient) {
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
         */
        public @Owning AsyncUploadStream build() throws IOException {
            CheckUtils.checkArgumentNotNull(m_hubClient);
            CheckUtils.checkArgumentNotNull(m_parentId);
            CheckUtils.checkArgumentNotNull(m_itemName);

            m_catalogClient = new CatalogServiceClient(m_hubClient, m_additionalHeaders);

            final var mediaType = m_isWorkflowLike ? CatalogServiceClient.KNIME_WORKFLOW_MEDIA_TYPE.toString()
                : MediaType.APPLICATION_OCTET_STREAM;
            final var uploadParts =
                    Math.min(CatalogServiceClient.MAX_NUM_PREFETCHED_UPLOAD_PARTS, NUMBER_OF_INITIAL_PARTS);
            final Map<String, ItemUploadRequest> uploadRequests = new LinkedHashMap<>();
            uploadRequests.put(m_itemName, new ItemUploadRequest(mediaType, uploadParts));

            // Initiate upload request
            final var preparedUploadOpt = m_catalogClient.initiateUpload(
                new ItemID(m_parentId), new UploadManifest(uploadRequests), m_parentETag);
            if (preparedUploadOpt.isEmpty()) {
                return null;
            }

            // Obtain upload instructions and create supplier for additional upload parts
            final var itemInstructions = preparedUploadOpt.get().getItems();
            m_uploadInstructions = CheckUtils.checkNotNull(itemInstructions.get(m_itemName));
            m_uploadId = m_uploadInstructions.getUploadId();

            return new AsyncUploadStream();
        }

    }

    /**
     * Asynchronous upload stream.
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    public final class AsyncUploadStream extends OutputStream {

        private static final int NUMBER_OF_PART_UPLOAD_RETRIES = 2;
        private static final int MAX_UPLOAD_STATUS_RETRY_TIME = 1_000;
        private static final int MAX_CHUNK_SIZE = 8 * (int)FileUtils.ONE_MB;

        private FilePartUploader m_filePartUploader;
        private @Owning ChunkingByteOutputStream m_chunkingOuputStream;

        private Deque<Future<Pair<Integer, EntityTag>>> m_pendingUploads = new ArrayDeque<>();
        private Map<Integer, EntityTag> m_finishedParts = new LinkedHashMap<>();

        private AsyncUploadStream() {
            // Create file part uploader to upload separate chunks of data
            m_filePartUploader = new FilePartUploader(m_hubClient.getApiClient().getConnectTimeout(),
                m_hubClient.getApiClient().getReadTimeout(), NUMBER_OF_PART_UPLOAD_RETRIES);

            // Create an empty branching execution monitor since we don't know the uploaded data size
            final var nullSplitterMonitor = BranchingExecMonitor.nullProgressMonitor(() -> false);

            // Create the supplier which can request additional upload parts
            final var partSupplier = new UploadPartSupplier(m_catalogClient, m_uploadInstructions);

            // Create chunking output stream
            m_chunkingOuputStream = createChunkingOutputStream(partSupplier, nullSplitterMonitor);
        }

        private @Owning ChunkingByteOutputStream createChunkingOutputStream(final UploadPartSupplier partSupplier,
            final BranchingExecMonitor splitter) {
            return new ChunkingByteOutputStream(MAX_CHUNK_SIZE, DigestUtils.getMd5Digest()) { // NOSONAR

                private final BranchingExecMonitor m_splitProgress = splitter;

                @Override
                public void chunkFinished(final int chunkNumber, final byte[] chunk, final byte[] hash)
                        throws IOException {
                    awaitPartFinished();

                    final var partNumber = chunkNumber + 1;
                    final LeafExecMonitor subMonitor = m_splitProgress.createLeafChild(Integer.toString(partNumber), 0);
                    m_pendingUploads.add(m_filePartUploader.uploadDataChunk(
                        m_itemName, partNumber, partSupplier, chunk, hash, subMonitor));
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    // Wait on the last chunk to be uploaded
                    awaitPartFinished();
                }

                private void awaitPartFinished() throws IOException {
                    Optional<Pair<Integer, EntityTag>> finishedPartOpt =
                            HubUploader.awaitPartFinished(m_pendingUploads);
                    if (finishedPartOpt.isPresent()) {
                        m_finishedParts.put(finishedPartOpt.get().getLeft(), finishedPartOpt.get().getRight());
                    }
                }

            };
        }

        @Override
        public void write(final int b) throws IOException {
            try {
                m_chunkingOuputStream.write(b);
            } catch (IOException e) {
                cancelUpload();
                throw e;
            }
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            try {
                m_chunkingOuputStream.write(b, off, len);
            } catch (IOException e) {
                cancelUpload();
                throw e;
            }
        }

        @Override
        public void write(final byte[] b) throws IOException {
            try {
                m_chunkingOuputStream.write(b);
            } catch (IOException e) {
                cancelUpload();
                throw e;
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                m_chunkingOuputStream.flush();
            } catch (IOException e) {
                cancelUpload();
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                if (m_chunkingOuputStream != null) {
                    // Closes the chunking output stream and uploads the remaining part
                    m_chunkingOuputStream.close();
                    // Reports back to catalog and waits for the upload to be completed
                    completeUpload();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Upload of '%s' has been aborted".formatted(m_itemName));
            } finally {
                m_chunkingOuputStream = null;
            }
        }

        private void completeUpload() throws IOException, InterruptedException {
            // Report to catalog that the upload is completed
            m_catalogClient.reportUploadFinished(m_uploadId, m_finishedParts);

            // Wait until the upload status is completed
            var numberOfStatusPolls = 1;
            var totalTime = 0;
            while (true) {
                final var state = m_catalogClient.pollUploadState(m_uploadId);
                LOGGER.atDebug() //
                .addArgument(m_itemName) //
                .addArgument(state.getStatus()) //
                .addArgument(state.getStatusMessage()) //
                .setMessage("Polling state of uploaded item '{}': {}, '{}'") //
                .log();

                switch (state.getStatus()) {
                    case ABORTED:
                        throw new IOException(state.getStatusMessage());
                    case ANALYSIS_PENDING, PREPARED:
                        break;
                    case COMPLETED:
                        return;
                    case FAILED:
                        throw new IOException(state.getStatusMessage());
                }

                var backOffTime =
                        (int)(MAX_UPLOAD_STATUS_RETRY_TIME / (1 + Math.exp(-Math.log(2)*(numberOfStatusPolls - 3))));
                Thread.sleep(backOffTime);
                totalTime += backOffTime;
                numberOfStatusPolls++;

                if (totalTime > m_timeout.toMillis()) {
                    throw new IOException("Upload exceeded timeout of %s ms".formatted(m_timeout.toMillis()));
                }
            }
        }

        private void cancelUpload() throws IOException {
            m_pendingUploads.forEach(pending -> pending.cancel(true));
            m_catalogClient.cancelUpload(m_uploadId);
        }

        /**
         * Cancels the upload process.
         *
         * @throws IOException if an I/O error occurred during cancellation
         */
        public void cancel() throws IOException {
            if (m_chunkingOuputStream != null) {
                m_chunkingOuputStream.close();
                m_chunkingOuputStream = null;
                cancelUpload();
            }
        }

    }

}
