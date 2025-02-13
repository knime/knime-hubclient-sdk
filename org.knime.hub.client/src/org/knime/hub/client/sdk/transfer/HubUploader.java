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
 *   Created on May 27, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.FileUtil;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.gateway.impl.webui.spaces.Collision;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.ent.Control;
import org.knime.hub.client.sdk.ent.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.ItemUploadRequest;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.RepositoryItem.RepositoryItemType;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.ent.UploadStarted;
import org.knime.hub.client.sdk.ent.UploadTarget;
import org.knime.hub.client.sdk.ent.WorkflowGroup;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.knime.hub.client.sdk.transfer.FilePartUploader.UploadTargetFetcher;
import org.knime.hub.client.sdk.Result.Failure;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.transfer.WorkflowExporter.ItemType;
import org.knime.hub.client.sdk.transfer.WorkflowExporter.ResourcesToCopy;

import com.google.common.util.concurrent.AtomicDouble;
import com.knime.enterprise.server.mason.Relation;
import com.knime.enterprise.server.rest.api.KnimeRelations;
import com.knime.enterprise.utility.KnimeServerConstants;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;

/**
 * Uploader for sets of items to a Hub.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public final class HubUploader extends AbstractHubTransfer {

    private static final String FORCE_ASYNC_UPLOAD_HOSTS_FEATURE_FLAG = "knime.hub.force_async_upload_hosts";

    private static final String MEDIA_TYPE_WORKFLOW_GROUP_NO_ZIP =
            StringUtils.removeEnd(KnimeServerConstants.MEDIA_TYPE_KNIME_WORKFLOW_GROUP, "+zip");

    private static final NodeLogger LOGGER = NodeLogger.getLogger(HubUploader.class);

    /**
     * Result of a check for collisions of a potential upload.
     *
     * @param parentGroup entity of the remote target group
     * @param parentGroupETag  entity tag of the remote target group
     * @param collisionLocations locations and descriptions of the found collisions
     * @param affected input paths with collisions
     */
    public record CollisionReport(WorkflowGroup parentGroup, EntityTag parentGroupETag,
            Map<IPath, Collision> collisionLocations, Map<IPath, IPath> affected) {}

    /**
     * Description of a local item to upload.
     *
     * @param type item type
     * @param fsPath location in the local file system
     */
    public record LocalItem(ItemType type, Path fsPath) {}

    /**
     * Description of an item to be uploaded.
     *
     * @param path path of the item inside the surrounding workflow group
     * @param localItem type and location of the local item to upload
     * @param uploadInstructions upload instructions
     */
    public record ItemToUpload(String path, LocalItem localItem, ItemUploadInstructions uploadInstructions) {}

    private final long m_chunkSize;

    private final FilePartUploader m_filePartUploader;

    /**
     * @param hubClient Hub API client
     * @param apHeaders Header parameters specific to AP
     * @param connectTimeout connect timeout for HTTP connections
     * @param readTimeout read timeout for HTTP connections
     * @param chunkSize size of the parts used in multi-part uploads
     * @param numPartUploadRetries number of times a failing part upload is being restarted before giving up
     */
    public HubUploader(final HubClientAPI hubClient, final Map<String, String> apHeaders, 
            final Duration connectTimeout, final Duration readTimeout, 
            final long chunkSize, final int numPartUploadRetries) {
        super(hubClient, apHeaders);
        m_chunkSize = chunkSize;
        m_filePartUploader = new FilePartUploader(connectTimeout, readTimeout, numPartUploadRetries);
    }

    /**
     * Checks for conflicts between the items to be uploaded and the contents of the target folder.
     *
     * @param parentId ID of the parent folder on the Hub
     * @param itemToType relative paths and item types of items to be uploaded
     * @param progMon to enable cancellation
     * @return collisions found if the Hub supports multi-part uploads, a {@link Failure} otherwise
     * @throws ResourceAccessException
     * @throws CanceledExecutionException
     */
    public Result<CollisionReport> checkCollisions(final ItemID parentId, final Map<IPath, ItemType> itemToType,
            final IProgressMonitor progMon) throws ResourceAccessException, CanceledExecutionException {
        progMon.beginTask("Checking for conflicts with existing items", IProgressMonitor.UNKNOWN);

        progMon.subTask("Fetching folder contents from Hub...");
        final var spaceParentControls = getMasonControlsOfSpaceParent(parentId);
        if (!supportsAsyncUpload(spaceParentControls)) {
            return Result.failure("This Hub does not support multi-part uploads", null);
        }

        final var optDeep = deepListItem(parentId, progMon::isCanceled);
        final Pair<RepositoryItem, EntityTag> parentGroupAndETag = optDeep.get();
        final var groupItem = parentGroupAndETag.getLeft();
        final WorkflowGroup parentGroup;
        if (groupItem instanceof WorkflowGroup wfGroup) {
            parentGroup = wfGroup;
        } else {
            throw new IllegalArgumentException("Upload target must be a space or folder, '%s' is of type '%s'" //
                .formatted(groupItem.getPath(), groupItem.getType()));
        }

        final Map<IPath, Collision> collisions = new LinkedHashMap<>();
        final Map<IPath, IPath> affected = new LinkedHashMap<>();
        for (final var pathAndType : itemToType.entrySet()) {
            final var relPath = pathAndType.getKey().makeRelative().removeTrailingSeparator();
            progMon.subTask("Analyzing '%s'".formatted(StringUtils.abbreviateMiddle(relPath.toString(), "...", 64)));
            final var localItemType = pathAndType.getValue();
            final var checkResult = checkForCollision(parentGroup, relPath, localItemType, spaceParentControls);
            if (checkResult.isPresent()) {
                final Pair<IPath, Collision> pathAndCollision = checkResult.get();
                final IPath collisionLocation = pathAndCollision.getLeft();
                affected.put(pathAndType.getKey(), collisionLocation);

                // add only the first collision for each path
                if (!collisions.containsKey(collisionLocation)) {
                    collisions.put(collisionLocation, pathAndCollision.getRight());
                }
            }
        }
        return Result.success(new CollisionReport(parentGroup, parentGroupAndETag.getRight(), collisions, affected));
    }

    private boolean supportsAsyncUpload(final Map<String, Control> spaceParentControls) {
        final var overwrittenHosts = List.of(System.getProperty(FORCE_ASYNC_UPLOAD_HOSTS_FEATURE_FLAG, "").split(","));
        return overwrittenHosts.contains(m_catalogClient.getHubAPIBaseURI().getHost())
                || spaceParentControls.containsKey(CatalogServiceClient.INITIATE_UPLOAD.toString());
    }

    private static Optional<Pair<IPath, Collision>> checkForCollision(final WorkflowGroup remoteItem, // NOSONAR
            final IPath path, final ItemType localItemType, final Map<String, Control> spaceControls) {
        // descend the remote tree and check all levels for conflicts
        RepositoryItem current = remoteItem;
        WorkflowGroup parent = null;
        for (var level = 0; level < path.segmentCount(); level++) {
            if (current instanceof WorkflowGroup group) {
                // (non-empty) local and remote folder, descend
                final var name = path.segment(level);
                parent = group;
                current = group.getChildren().stream() //
                    .filter(item -> IPath.forPosix(item.getPath()).lastSegment().equals(name)) //
                    .findAny().orElse(null);
                if (current == null) {
                    if (spaceControls.containsKey(KnimeRelations.UPLOAD.toString())) { // NOSONAR
                        // ancestor item doesn't exist but can be created, no conflict
                        return Optional.empty();
                    } else {
                        // can't create ancestor folder, nothing can be done
                        return Optional.of(Pair.of(path.uptoSegment(level), new Collision(false, false, false)));
                    }
                }
            } else {
                // conflict between a local ancestor folder and a non-folder item on Hub
                final boolean canUploadToParent =
                        parent == null || spaceControls.containsKey(KnimeRelations.UPLOAD.toString());
                return Optional.of(Pair.of(path.uptoSegment(level),
                    new Collision(false, false, canUploadToParent)));
            }
        }

        // reached the end, and an item at the path already exists
        final boolean remoteIsFolder = current instanceof WorkflowGroup;
        final boolean localIsFolder = localItemType == ItemType.WORKFLOW_GROUP;
        final boolean canUploadToParent = parent != null
                && spaceControls.containsKey(KnimeRelations.UPLOAD.toString());
        if (remoteIsFolder && localIsFolder) {
            // copying an empty folder over an existing one is not a conflict
            return Optional.empty();
        } else if (remoteIsFolder || localIsFolder) {
            // conflict between leaf item and folder
            return Optional.of(Pair.of(path, new Collision(false, false, canUploadToParent)));
        } else {
            // collision between two leaf items
            return Optional.of(Pair.of(path, //
                new Collision( //
                    isTypeCompatible(current.getType(), localItemType), //
                    spaceControls.containsKey(Relation.EDIT.toString()), //
                    canUploadToParent)));
        }
    }

    private static boolean isTypeCompatible(final RepositoryItemType oldType, final ItemType newType) {
        return switch (oldType) {
        case WORKFLOW, COMPONENT -> newType == ItemType.WORKFLOW_LIKE;
        case WORKFLOW_GROUP, SPACE -> newType == ItemType.WORKFLOW_GROUP;
        case DATA -> newType == ItemType.DATA_FILE;
        };
    }

    /**
     * Tries to initiate an upload.
     *
     * @param parentId ID of the containing workflow group
     * @param items mapping from path inside the surrounding group to info about the item to be uploaded
     * @param parentTag expected entity tag of the surrounding group, may be {@code null}
     * @param numInitialParts number of initial part upload URLs to request per non-folder item
     * @return mapping from path to item upload instructions, or {@link Optional#empty()} if the parent has changed
     * @throws ResourceAccessException if the request failed
     */
    public Optional<Map<IPath, ItemToUpload>> initiateUpload(final ItemID parentId,
            final Map<IPath, LocalItem> items, final EntityTag parentTag,
            final int numInitialParts) throws ResourceAccessException {

        final Map<String, ItemUploadRequest> uploadRequests = new LinkedHashMap<>();
        int partsRemaining = CatalogServiceClient.MAX_NUM_PREFETCHED_UPLOAD_PARTS;
        for (final var entry : items.entrySet()) {
            final var itemType = entry.getValue().type();
            final String mediaType = switch (itemType) {
                case WORKFLOW_GROUP -> MEDIA_TYPE_WORKFLOW_GROUP_NO_ZIP;
                case WORKFLOW_LIKE -> KnimeServerConstants.MEDIA_TYPE_KNIME_WORKFLOW;
                case DATA_FILE -> MediaType.APPLICATION_OCTET_STREAM;
            };
            final var partsHere = itemType == ItemType.WORKFLOW_GROUP ? 0 : Math.min(partsRemaining, numInitialParts);
            uploadRequests.put(entry.getKey().toString(), new ItemUploadRequest(mediaType, partsHere));
            partsRemaining -= partsHere;
        }

        final Optional<UploadStarted> optInstructions =
                m_catalogClient.initiateUpload(parentId, new UploadManifest(uploadRequests), parentTag);
        if (optInstructions.isEmpty()) {
            return Optional.empty();
        }

        final var instructions = optInstructions.get().getItems();
        final Map<IPath, ItemToUpload> out = new LinkedHashMap<>();
        for (final var item : items.entrySet()) {
            final var pathInTarget = item.getKey();
            final var pathStr = pathInTarget.toString();
            if (item.getValue().type() != ItemType.WORKFLOW_GROUP) {
                final var uploadInstructions = CheckUtils.checkNotNull(instructions.get(pathStr));
                out.put(pathInTarget, new ItemToUpload(pathStr, item.getValue(), uploadInstructions));
            }
        }

        return Optional.of(out);
    }

    private record FileResources(Map<String, ResourcesToCopy> workflowResources, Map<String, Long> sizes,
        long totalSize) {
    }

    /**
     * Performs the uploads.
     *
     * @param itemsToUpload items to be uploaded
     * @param excludeData whether or not to exclude data when exporting workflows
     * @param progMon progress monitor
     * @return map from item path to upload result, or {@link Optional#empty()} if the upload was aborted
     * @throws IOException if the upload as a whole failed
     * @throws CanceledExecutionException if the upload was canceled
     */
    public Map<IPath, Result<String>> performUpload(final Map<IPath, ItemToUpload> itemsToUpload,
            final boolean excludeData, final IProgressMonitor progMon)
            throws IOException, CanceledExecutionException {
        final var resources =
                collectFileResources(itemsToUpload, new WorkflowExporter(excludeData), progMon);
        final var uploadJobs = submitUploadJobs(itemsToUpload, resources, progMon);
        return awaitUploads(uploadJobs, progMon::isCanceled);
    }

    private Map<IPath, Future<Result<String>>> submitUploadJobs(final Map<IPath, ItemToUpload> itemsToUpload,
            final FileResources resources, final IProgressMonitor progMon) {
        final Map<IPath, Future<Result<String>>> uploadJobs = new LinkedHashMap<>();
        if (itemsToUpload.size() == 1) {
            // only one item is being uploaded, show part uploads as details
            final var pathInTarget = itemsToUpload.keySet().iterator().next();
            final var itemToUpload = itemsToUpload.get(pathInTarget);
            final var itemType = itemToUpload.localItem().type();
            final String title = "Uploading '%s'...".formatted(pathInTarget);
            final var numPartsIfKnown =
                itemType != ItemType.DATA_FILE ? " parts of %s".formatted(bytesToHuman(m_chunkSize))
                    : "/%d parts".formatted((resources.totalSize() + m_chunkSize - 1) / m_chunkSize);
            final var splitter = beginMultiProgress(progMon, title, status -> {
                final var firstLine = "Uploading '%s': %d%s transferred (%.1f%%, %s/sec)" //
                    .formatted(pathInTarget, status.numDone(), numPartsIfKnown,
                        100.0 * status.totalProgress(), bytesToHuman(status.bytesPerSecond()));
                progMon.setTaskName(firstLine);
                progMon.subTask(status.active().stream() //
                    .map(e -> " \u2022 %s of file part %s".formatted(percentage(e.getValue()), e.getKey())) //
                    .collect(Collectors.joining("\n")));
            });
            submitUploadJob(uploadJobs, pathInTarget, resources, splitter, itemToUpload);
        } else {
            // multiple items to upload, each one is one line of detail
            final var splitter = beginMultiProgress(progMon, "Uploading items...", status -> {
                final var firstLine = "Uploading: %d/%d items transferred (%.1f%%, %s/sec)" //
                    .formatted(status.numDone(), itemsToUpload.size(), 100.0 * status.totalProgress(),
                        bytesToHuman(status.bytesPerSecond()));
                progMon.setTaskName(firstLine);
                progMon.subTask(status.active().stream() //
                    .map(e -> " \u2022 %s of '%s'".formatted(percentage(e.getValue()), shortenedPath(e.getKey()))) //
                    .collect(Collectors.joining("\n")));
            });

            for (final var item : itemsToUpload.entrySet()) {
                final var pathInTarget = item.getKey();
                final var itemToUpload = item.getValue();
                final String pathStr = itemToUpload.path();
                final var size = resources.sizes().get(pathStr);
                final var totalSize = resources.totalSize();
                final double contribution = totalSize == 0 ? 0 : (1.0 * size / totalSize);
                final var subSplitter = splitter.createBranchingChild(pathStr, contribution);
                submitUploadJob(uploadJobs, pathInTarget, resources, subSplitter, itemToUpload);
            }
        }
        return uploadJobs;
    }

    private void submitUploadJob(final Map<IPath, Future<Result<String>>> uploadJobs, final IPath pathInTarget,
        final FileResources resources, final BranchingExecMonitor subSplitter, final ItemToUpload itemToUpload) {
        uploadJobs.put(pathInTarget, HUB_ITEM_TRANSFER_POOL.submit( //
            () -> uploadItem(itemToUpload.path(), itemToUpload.localItem().fsPath(), itemToUpload.localItem().type(),
                resources.workflowResources(), subSplitter, itemToUpload.uploadInstructions())));
    }

    private static Map<IPath, Result<String>> awaitUploads(final Map<IPath, Future<Result<String>>> uploadJobs,
            final BooleanSupplier cancelChecker) throws CanceledExecutionException {
        Map<IPath, Result<String>> uploaded = new LinkedHashMap<>();
        var canceled = false;
        for (final var unfinishedJob : uploadJobs.entrySet()) {
            final var path = unfinishedJob.getKey();
            final var future = unfinishedJob.getValue();

            try {
                final var uploadResult = waitForCancellable(future, cancelChecker, throwable -> {
                    if (throwable instanceof CanceledExecutionException cee) { // NOSONAR
                        throw cee;
                    } else {
                        LOGGER.debug(() -> "Unexpected exception during upload job", throwable);
                        return Result.failure(throwable.getMessage(), throwable);
                    }
                });
                uploaded.put(path, uploadResult);
            } catch (CanceledExecutionException cee) { // NOSONAR
                // we continue to collect results to we can clean up already finished downloads
                canceled = true;
            }
        }
        if (canceled) {
            throw new CanceledExecutionException();
        }
        return uploaded;
    }

    private static FileResources collectFileResources(final Map<IPath, ItemToUpload> itemsToUpload,
            final WorkflowExporter exporter, final IProgressMonitor progMon)
            throws IOException, CanceledExecutionException {

        progMon.setTaskName("Preparing items for upload");
        final Map<String, ResourcesToCopy> workflowResources = new LinkedHashMap<>();
        final Map<String, Long> sizes = new LinkedHashMap<>();

        var totalSize = 0L;
        for (final var item : itemsToUpload.entrySet()) {
            if (progMon.isCanceled()) {
                throw new CanceledExecutionException();
            }
            final var itemToUpload = item.getValue();
            final var pathStr = itemToUpload.path();
            final var localItem = itemToUpload.localItem();
            final var fsPath = localItem.fsPath();
            final var type = localItem.type();

            final long size;
            if (type == ItemType.WORKFLOW_GROUP) {
                size = 0L;
            } else if (type == ItemType.WORKFLOW_LIKE) {
                final var resources = exporter.collectResourcesToCopy(Set.of(fsPath), fsPath.getParent());
                workflowResources.put(pathStr, resources);
                size = resources.numBytes();
            } else {
                size = Files.size(fsPath);
            }

            sizes.put(pathStr, size);
            totalSize += size;
        }

        return new FileResources(workflowResources, sizes, totalSize);
    }

    private Result<String> uploadItem(final String path, final Path osPath, final ItemType type,
            final Map<String, ResourcesToCopy> workflowResources, final BranchingExecMonitor splitter,
            final ItemUploadInstructions instructions) throws IOException, CanceledExecutionException {
        try {
            if (type == ItemType.DATA_FILE) {
                uploadDataFile(path, osPath, instructions, splitter);
            } else {
                uploadWorkflowLike(path, workflowResources.get(path), instructions, splitter);
            }

            while (true) {
                final var state = m_catalogClient.pollUploadState(instructions.getUploadId());
                LOGGER.debug(() -> "Polling state of uploaded item '%s': %s, '%s'" //
                    .formatted(path, state.getStatus(), state.getStatusMessage()));
                switch (state.getStatus()) {
                    case ABORTED:
                        return Result.failure(state.getStatusMessage(), null);
                    case ANALYSIS_PENDING, PREPARED:
                        break;
                    case COMPLETED:
                        return Result.success(state.getStatusMessage());
                    case FAILED:
                        return Result.failure(state.getStatusMessage(), null);
                }
                Thread.sleep(1_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure("Upload of '%s' has been aborted".formatted(path), null);
        }
    }

    private void uploadWorkflowLike(final String path, final ResourcesToCopy export,
            final ItemUploadInstructions instructions, final BranchingExecMonitor splitter)
            throws CanceledExecutionException, IOException {

        // remember all temp files so they can be deleted cleaned up `finally` if something went wrong
        final List<Path> tempFiles = new ArrayList<>();

        final Deque<Future<Pair<Integer, EntityTag>>> pendingUploads = new ArrayDeque<>();
        var success = false;
        try {
            final var partSupplier = new UploadPartSupplier(instructions);
            final var currentWriteProgress = new AtomicDouble();
            final FailableConsumer<Double, CanceledExecutionException> updater = progress -> {
                splitter.checkCanceled();
                currentWriteProgress.set(progress);
            };

            try (final var outStream = uploadingOutputStream(path, partSupplier, pendingUploads::addLast, tempFiles,
                    currentWriteProgress::get, splitter)) {
                export.exportInto(outStream, updater);
            }

            // removes finished jobs from `pendingUploads`
            final Map<Integer, EntityTag> finishedParts = awaitPartsFinished(pendingUploads, splitter.cancelChecker());
            success = true;

            m_catalogClient.reportUploadFinished(instructions.getUploadId(), finishedParts);
        } finally {
            if (!success) {
                LOGGER.debug(() -> "Upload of '%s' has failed, cancelling parts and notifying Catalog Service" //
                    .formatted(path));
                // the upload didn't finish successfully, cancel pending part uploads and delete all temp files
                pendingUploads.forEach(pending -> pending.cancel(true));
                m_catalogClient.cancelUpload(instructions.getUploadId());
                for (final var tempFile : tempFiles) {
                    Files.deleteIfExists(tempFile);
                }
            }
        }
    }

    private static Map<Integer, EntityTag> awaitPartsFinished(
            final Deque<Future<Pair<Integer, EntityTag>>> pendingUploads, final BooleanSupplier cancelChecker)
            throws IOException, CanceledExecutionException {
        final Map<Integer, EntityTag> finished = new LinkedHashMap<>();
        while (!pendingUploads.isEmpty()) {
            final var result = waitForCancellable(pendingUploads.getFirst(), cancelChecker, throwable -> {
                if (throwable instanceof IOException ioe) {
                    throw ioe;
                } else {
                    LOGGER.debug(() -> "Unexpected exception while uploading file part", throwable);
                    throw ExceptionUtils.asRuntimeException(throwable);
                }
            });
            pendingUploads.removeFirst();
            result.accept(finished::put);
        }
        return finished;
    }

    private OutputStream uploadingOutputStream(final String path, final UploadPartSupplier partSupplier,
            final Consumer<Future<Pair<Integer, EntityTag>>> partUploads, final List<Path> chunks,
            final DoubleSupplier currentWriteProgress, final BranchingExecMonitor splitter) {

        return new ChunkingFileOutputStream(m_chunkSize, DigestUtils.getMd5Digest()) { // NOSONAR

            private final BranchingExecMonitor m_splitProgress = splitter;

            private double m_progress;

            @Override
            public Path newOutputFile() throws IOException {
                return FileUtil.createTempFile("KNIMEWorkflowUpload", ".knwf.part").toPath();
            }

            @Override
            public void chunkFinished(final int chunkNumber, final Path chunk, final long fileSize, final byte[] hash)
                    throws ResourceAccessException {
                chunks.add(chunk);

                final var partNumber = chunkNumber + 1;
                final var newProgress = currentWriteProgress.getAsDouble();
                final LeafExecMonitor subMonitor =
                        m_splitProgress.createLeafChild(Integer.toString(partNumber), newProgress - m_progress);
                m_progress = newProgress;

                partUploads.accept(m_filePartUploader.uploadTempFile(path, partNumber, partSupplier, chunk, fileSize,
                    hash, subMonitor));
            }
        };
    }

    private void uploadDataFile(final String path, final Path dataFile, final ItemUploadInstructions instructions,
            final BranchingExecMonitor splitter) throws IOException, CanceledExecutionException {

        final var numBytes = Files.size(dataFile);
        final var partSupplier = new UploadPartSupplier(instructions);

        final Deque<Future<Pair<Integer, EntityTag>>> partUploads = new ArrayDeque<>();
        var success = false;
        try {
            // do/while because we always want to create at least one chunk
            long pos = 0;
            var partNumber = 1;
            do {
                final var chunkSize = Math.min(m_chunkSize, numBytes - pos);
                final var maxProg = numBytes == 0 ? 0 : (1.0 * chunkSize / numBytes);
                final LeafExecMonitor subMonitor = splitter.createLeafChild(Integer.toString(partNumber), maxProg);
                final var offsetInFile = pos;
                final var partNo = partNumber;

                partUploads.add(m_filePartUploader.uploadDataFilePart(path, partNo, partSupplier, dataFile,
                    offsetInFile, chunkSize, subMonitor));
                pos += chunkSize;
                partNumber++;
            } while (pos < numBytes);

            final Map<Integer, EntityTag> finishedParts = awaitPartsFinished(partUploads, splitter.cancelChecker());
            success = true;

            m_catalogClient.reportUploadFinished(instructions.getUploadId(), finishedParts);
        } finally {
            if (!success) {
                LOGGER.debug(() -> "Upload of '%s' has failed, cancelling parts and notifying Hub".formatted(path));
                partUploads.forEach(pending -> pending.cancel(true));
                m_catalogClient.cancelUpload(instructions.getUploadId());
            }
        }
    }

    private final class UploadPartSupplier implements UploadTargetFetcher {

        private final String m_uploadId;

        private final Map<Integer, UploadTarget> m_initialParts;

        public UploadPartSupplier(final ItemUploadInstructions instructions) {
            m_uploadId = instructions.getUploadId();
            m_initialParts = new LinkedHashMap<>(instructions.getParts().orElse(Map.of()));
        }

        @Override
        public UploadTarget fetch(final int partNo) throws ResourceAccessException {
            // get and remove initial part, a new one will be requested if this one didn't work
            final var initial = m_initialParts.remove(partNo);
            return initial != null ? initial : m_catalogClient.requestAdditionalUploadPart(m_uploadId, partNo);
        }
    }
}
