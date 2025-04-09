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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.WorkflowExporter.ItemType;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.hub.client.sdk.api.CatalogClient;
import org.knime.hub.client.sdk.ent.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.ItemUploadRequest;
import org.knime.hub.client.sdk.ent.RepositoryItem.RepositoryItemType;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.ent.UploadStarted;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.knime.hub.client.sdk.transfer.HubUploader.UploadPartSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.RuntimeDelegate;
import jakarta.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

/**
 * Creates an asynchronous upload stream to the hub.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public final class AsyncUploadStream extends OutputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncUploadStream.class);

    static final HeaderDelegate<EntityTag> ETAG_DELEGATE =
            RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);

    private static final int NUMBER_OF_INITIAL_PARTS = 1;

    private static final int MAX_CHUNK_SIZE = 8 * (int)FileUtils.ONE_MB;

    private @NotOwning CatalogClient m_hubClient;

    private CatalogServiceClient m_catalogClient;

    private String m_itemName;

    private String m_uploadId;

    private int m_numOfPartUploadRetries;

    private FilePartUploader m_filePartUploader;

    private @Owning ChunkingByteOutputStream m_chunkingOuputStream;

    private CircularFifoQueue<Future<Pair<Integer, EntityTag>>> m_pendingUploads = new CircularFifoQueue<>(1);

    private Map<Integer, EntityTag> m_finishedParts = new LinkedHashMap<>();

    private boolean m_isCanceled;

    private AsyncUploadStream(@Owning final CatalogClient hubClient, final String itemName,
        final ItemToUpload itemToUpload, final Map<String, String> additionalHeaders) {
        m_hubClient = hubClient;
        m_catalogClient = new CatalogServiceClient(hubClient, additionalHeaders);
        m_itemName = itemName;
        m_uploadId = itemToUpload.uploadInstructions().getUploadId();
        m_numOfPartUploadRetries = 2;
        m_filePartUploader = new FilePartUploader(hubClient.getApiClient().getConnectTimeout(),
            hubClient.getApiClient().getReadTimeout(), m_numOfPartUploadRetries);

        // Create output stream (upload already initiated)
        final var instructions = itemToUpload.uploadInstructions();
        final var partSupplier = new UploadPartSupplier(m_catalogClient, instructions);

        // Create an empty branching execution monitor which is just used to cancel the part uploads
        final var nullSplitterMonitor = new BranchingExecMonitor(this::isCanceled);
        m_chunkingOuputStream = createChunkingOutputStream(partSupplier, nullSplitterMonitor, this::isCanceled);
    }

    private @Owning ChunkingByteOutputStream createChunkingOutputStream(final UploadPartSupplier partSupplier,
        final BranchingExecMonitor splitter, final BooleanSupplier cancelChecker) {
        return new ChunkingByteOutputStream(MAX_CHUNK_SIZE, DigestUtils.getMd5Digest()) { // NOSONAR

            private final BranchingExecMonitor m_splitProgress = splitter;

            @Override
            public void chunkFinished(final int chunkNumber, final byte[] chunk, final byte[] hash) throws IOException {
                awaitPartFinished();

                final var partNumber = chunkNumber + 1;
                final LeafExecMonitor subMonitor = m_splitProgress.createLeafChild(Integer.toString(partNumber), 0);
                m_pendingUploads.add(m_filePartUploader.uploadDataChunk(
                    m_itemName.toString(), partNumber, partSupplier, chunk, hash, subMonitor));
            }

            @Override
            public void close() throws IOException {
                super.close();
                // Wait on the last chunk to be uploaded
                awaitPartFinished();
            }

            private void awaitPartFinished() throws IOException {
                Optional<Pair<Integer, EntityTag>> finishedPartOpt =
                        HubUploader.awaitPartFinished(m_pendingUploads, cancelChecker);
                if (finishedPartOpt.isPresent()) {
                    m_finishedParts.put(finishedPartOpt.get().getLeft(), finishedPartOpt.get().getRight());
                }
            }

        };
    }

    /**
     * Creates an asynchronous upload stream to the hub.
     *
     * @param catalogClient {@link CatalogClient}
     * @param itemName the relative path of the item in the parent group
     * @param parentId the ID of the parent group
     * @param parentEtag the entity tag of the parent group
     * @param uploadType the {@link ItemType} of the uploaded item
     * @param additionalHeaders additional header parameters
     *
     * @return {@link AsyncUploadStream}
     * @throws IOException if an I/O error occurred during the upload
     */
    public static @Owning AsyncUploadStream createAsyncUploadStream(final CatalogClient catalogClient,
        final String itemName, final String parentId, final EntityTag parentEtag, final RepositoryItemType uploadType,
        final Map<String, String> additionalHeaders) throws IOException {
        if (RepositoryItemType.WORKFLOW_GROUP == uploadType) {
            throw new IOException("Can't upload workflow group");
        }

        // Initiate upload request
        final var preparedUploadOpt = initiateUpload(catalogClient, parentId, parentEtag, IPath.forPosix(itemName),
            uploadType, NUMBER_OF_INITIAL_PARTS, additionalHeaders);
        if (preparedUploadOpt.isEmpty()) {
            // an empty `Optional` here means that the parent has changed
            throw new IOException(
                "Could not initiate upload of %s, parent %s has changed".formatted(itemName, parentId));
        }
        return new AsyncUploadStream(catalogClient, itemName, preparedUploadOpt.get(), additionalHeaders);
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
        // NOOP
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
        while (true) {
            final var state = m_catalogClient.pollUploadState(m_uploadId);
            LOGGER.debug("Polling state of uploaded item '%s': %s, '%s'".formatted(
                m_itemName, state.getStatus(), state.getStatusMessage()));
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
            Thread.sleep(1_000);
        }
    }

    private void cancelUpload() throws IOException {
        m_pendingUploads.forEach(pending -> pending.cancel(true));
        m_catalogClient.cancelUpload(m_uploadId);
    }

    private static Optional<ItemToUpload> initiateUpload(final CatalogClient catalogClient, final String parentId,
        final EntityTag parentETag, final IPath relativeItemPath, final RepositoryItemType itemType,
        final int numInitialParts, final Map<String, String> additionalHeaders) throws ResourceAccessException {
        Map<String, String> requestHeaders = new HashMap<>(additionalHeaders);
        if (parentETag != null) {
            requestHeaders.put(HttpHeaders.IF_MATCH, ETAG_DELEGATE.toString(parentETag));
        }

        final String mediaType = switch (itemType) {
            case WORKFLOW, COMPONENT -> CatalogServiceClient.KNIME_WORKFLOW_MEDIA_TYPE.toString();
            case DATA -> MediaType.APPLICATION_OCTET_STREAM;
            default -> throw new IllegalArgumentException("Unexpected item type: " + itemType);
        };

        final var uploadParts = Math.min(CatalogServiceClient.MAX_NUM_PREFETCHED_UPLOAD_PARTS, numInitialParts);
        final Map<String, ItemUploadRequest> uploadRequests = new LinkedHashMap<>();
        final var relativeItemPathString = relativeItemPath.toString();
        uploadRequests.put(relativeItemPathString, new ItemUploadRequest(mediaType, uploadParts));

        Optional<UploadStarted> optInstructions;
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = catalogClient.initiateUpload(parentId, new UploadManifest(uploadRequests),
                        CatalogServiceClient.SLOW_OPERATION_READ_TIMEOUT, requestHeaders);
            if (response.statusCode() == Status.PRECONDITION_FAILED.getStatusCode()) {
                optInstructions = Optional.empty();
            } else {
                optInstructions = Optional.ofNullable(CatalogServiceClient.checkSuccessful(response).value());
            }
        } catch (CouldNotAuthorizeException e) {
            throw new ResourceAccessException("Could not authorize Hub REST call: %s".formatted(e.getMessage()), e);
        } catch (IOException e) {
            throw new ResourceAccessException(e.getMessage(), e);
        }
        if (optInstructions.isEmpty()) {
            return Optional.empty();
        }

        final var instructions = optInstructions.get().getItems();
        final var uploadInstructions = CheckUtils.checkNotNull(instructions.get(relativeItemPathString));
        return Optional.of(new ItemToUpload(itemType, uploadInstructions));
    }

    /**
     * Cancels the upload process.
     *
     * @throws IOException if an I/O error occurred during cancellation
     */
    public void cancel() throws IOException {
        m_isCanceled = true;
        cancelUpload();
    }

    private boolean isCanceled() {
        return m_isCanceled;
    }

    private record ItemToUpload(RepositoryItemType uploadType, ItemUploadInstructions uploadInstructions) {}

}
