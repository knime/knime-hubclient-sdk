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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.WorkflowExporter;
import org.knime.core.node.workflow.WorkflowExporter.ItemType;
import org.knime.core.node.workflow.WorkflowExporter.ResourcesToCopy;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.FailureType;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.Result.Failure;
import org.knime.hub.client.sdk.Result.Success;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.ent.Control;
import org.knime.hub.client.sdk.ent.ProblemDescription;
import org.knime.hub.client.sdk.ent.catalog.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.catalog.ItemUploadRequest;
import org.knime.hub.client.sdk.ent.catalog.RepositoryItem;
import org.knime.hub.client.sdk.ent.catalog.RepositoryItem.RepositoryItemType;
import org.knime.hub.client.sdk.ent.catalog.UploadManifest;
import org.knime.hub.client.sdk.ent.catalog.UploadStarted;
import org.knime.hub.client.sdk.ent.catalog.UploadStatus;
import org.knime.hub.client.sdk.ent.catalog.UploadTarget;
import org.knime.hub.client.sdk.ent.catalog.WorkflowGroup;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.ProgressPoller;
import org.knime.hub.client.sdk.transfer.FilePartUploader.UploadTargetFetcher;
import org.knime.hub.client.sdk.transfer.internal.URLConnectionUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicDouble;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

/**
 * Uploader for sets of items to a Hub.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public final class HubUploader extends AbstractHubTransfer {

    private static final String DISABLE_ARTIFACT_UPLOAD_PROPERTY = "knime.hub.client.disableArtifactUpload";

    /** Maximum number of pre-fetched upload URLs per upload. */
    static final int MAX_NUM_PREFETCHED_UPLOAD_PARTS = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(HubUploader.class);

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
     * Uploader to a hub instance which supports the asynchronous upload flow
     *
     * @param hubClient Hub API client
     * @param apHeaders Header parameters specific to AP
     * @param chunkSize size of the parts used in multi-part uploads
     * @param numPartUploadRetries number of times a failing part upload is being restarted before giving up
     */
    public HubUploader(final HubClientAPI hubClient, final Map<String, String> apHeaders, final long chunkSize,
        final int numPartUploadRetries) {
        super(hubClient, apHeaders);
        m_chunkSize = chunkSize;
        m_filePartUploader = new FilePartUploader(URLConnectionUploader.INSTANCE, numPartUploadRetries, false);
    }

    /**
     * Checks for conflicts between the items to be uploaded and the contents of the target folder.
     *
     * @param parentId ID of the parent folder on the Hub
     * @param itemToType relative paths and item types of items to be uploaded
     * @param progMon to enable cancellation
     * @return collisions found if the Hub supports multi-part uploads, a {@link Failure} otherwise
     * @throws CancelationException if the upload was canceled
     */
    public Result<Optional<CollisionReport>, FailureValue> checkCollisions(final ItemID parentId,
        final Map<IPath, ItemType> itemToType, final IProgressMonitor progMon) throws CancelationException {
        progMon.beginTask("Checking for conflicts with existing items", IProgressMonitor.UNKNOWN);
        progMon.subTask("Fetching folder contents from Hub...");

        final var deepRes = deepListItem(parentId, progMon::isCanceled);
        if (!(deepRes instanceof Success<TaggedRepositoryItem, FailureValue> success)) {
            return deepRes.asFailure();
        }

        final var parentGroupAndETag = success.value();
        final var groupItem = parentGroupAndETag.item();
        final var groupItemControls = groupItem.getMasonControls();
        if (!supportsAsyncUpload(groupItemControls)) {
            return Result.success(Optional.empty());
        }

        if (!(groupItem instanceof WorkflowGroup parentGroup)) {
            throw new IllegalArgumentException("Upload target must be a space or folder, '%s' is of type '%s'" //
                .formatted(groupItem.getPath(), groupItem.getType()));
        }

        final Map<IPath, Collision> collisions = new LinkedHashMap<>();
        final Map<IPath, IPath> affected = new LinkedHashMap<>();
        for (final var pathAndType : itemToType.entrySet()) {
            final var relPath = pathAndType.getKey().makeRelative().removeTrailingSeparator();
            progMon.subTask("Analyzing '%s'".formatted(StringUtils.abbreviateMiddle(relPath.toString(), "...", 64)));
            final var localItemType = pathAndType.getValue();
            final var checkResult = checkForCollision(parentGroup, relPath, localItemType, groupItemControls);
            if (checkResult.isPresent()) {
                final Pair<IPath, Collision> pathAndCollision = checkResult.get();
                final IPath collisionLocation = pathAndCollision.getLeft();
                affected.put(pathAndType.getKey(), collisionLocation);

                // add only the first collision for each path
                collisions.computeIfAbsent(collisionLocation, k -> pathAndCollision.getRight());
            }
        }
        final var collisionReport = new CollisionReport(parentGroup, parentGroupAndETag.etag(), collisions, affected);
        return Result.success(Optional.of(collisionReport));
    }

    /**
     * Verifies that the asynchronous upload is supported on the hub instance.
     *
     * @param spaceParentControls mason controls of the parent group
     * @return <code>true</code> if asynchronous upload flow is supported
     */
    public static boolean supportsAsyncUpload(final Map<String, Control> spaceParentControls) {
        if (Boolean.parseBoolean(System.getProperty(DISABLE_ARTIFACT_UPLOAD_PROPERTY))) {
            return false;
        }
        return spaceParentControls.containsKey(INITIATE_UPLOAD);
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
                    if (spaceControls.containsKey(UPLOAD)) { // NOSONAR
                        // ancestor item doesn't exist but can be created, no conflict
                        return Optional.empty();
                    } else {
                        // can't create ancestor folder, nothing can be done
                        return Optional.of(Pair.of(path.uptoSegment(level), new Collision(false, false, false)));
                    }
                }
            } else {
                // conflict between a local ancestor folder and a non-folder item on Hub
                final boolean canUploadToParent = parent == null || spaceControls.containsKey(UPLOAD);
                return Optional.of(Pair.of(path.uptoSegment(level), new Collision(false, false, canUploadToParent)));
            }
        }

        // reached the end, and an item at the path already exists
        final boolean remoteIsFolder = current instanceof WorkflowGroup;
        final boolean localIsFolder = localItemType == ItemType.WORKFLOW_GROUP;
        final boolean canUploadToParent = parent != null && spaceControls.containsKey(UPLOAD);
        if (remoteIsFolder && localIsFolder) {
            // copying an empty folder over an existing one is not a conflict
            return Optional.empty();
        } else if (remoteIsFolder || localIsFolder) {
            // conflict between leaf item and folder
            return Optional.of(Pair.of(path, new Collision(false, false, canUploadToParent)));
        } else {
            // collision between two leaf items
            return Optional.of(Pair.of(path, //
                new Collision(isTypeCompatible(current.getType(), localItemType), spaceControls.containsKey(EDIT),
                    canUploadToParent)));
        }
    }

    private static boolean isTypeCompatible(final RepositoryItemType oldType, final ItemType newType) {
        return switch (oldType) {
        case WORKFLOW, COMPONENT -> newType == ItemType.WORKFLOW_LIKE;
        case WORKFLOW_GROUP, SPACE -> newType == ItemType.WORKFLOW_GROUP;
        case DATA -> newType == ItemType.DATA_FILE;
        case SNAPSHOT, TRASH, UNKNOWN, WORKFLOW_TEMPLATE ->
            throw new IllegalArgumentException("Unexpected server item type: " + oldType);
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
     * @throws HubFailureIOException if a request to Hub failed
     */
    public Optional<Map<IPath, ItemToUpload>> initiateUpload(final ItemID parentId,
            final Map<IPath, LocalItem> items, final EntityTag parentTag,
            final int numInitialParts) throws HubFailureIOException {

        final Map<String, ItemUploadRequest> uploadRequests = new LinkedHashMap<>();
        int partsRemaining = MAX_NUM_PREFETCHED_UPLOAD_PARTS;
        for (final var entry : items.entrySet()) {
            final var itemType = entry.getValue().type();
            final String mediaType = switch (itemType) {
                case WORKFLOW_GROUP -> KNIME_WORKFLOW_GROUP_TYPE.toString();
                case WORKFLOW_LIKE -> KNIME_WORKFLOW_TYPE_ZIP.toString();
                case DATA_FILE -> MediaType.APPLICATION_OCTET_STREAM;
            };
            final var partsHere = itemType == ItemType.WORKFLOW_GROUP ? 0 : Math.min(partsRemaining, numInitialParts);
            uploadRequests.put(entry.getKey().toString(), new ItemUploadRequest(mediaType, partsHere));
            partsRemaining -= partsHere;
        }

        final var optInstructions = initiateUpload(m_catalogClient, m_clientHeaders, parentId,
            new UploadManifest(uploadRequests), parentTag).orElseThrow(HubFailureIOException::new);
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
     * @param exporter exporter to use for workflow exports
     * @param tempFileSupplier provider for temp files
     * @param progMon progress monitor
     * @return map from item path to upload result, or {@link Optional#empty()} if the upload was aborted
     * @throws HubFailureIOException if the upload as a whole failed
     * @throws CancelationException if the upload was canceled
     */
    public Map<IPath, Result<String, FailureValue>> performUpload(final Map<IPath, ItemToUpload> itemsToUpload,
            final WorkflowExporter<CancelationException> exporter, final TempFileSupplier tempFileSupplier,
            final IProgressMonitor progMon) throws CancelationException, HubFailureIOException {
        try (final var poller = ConcurrentExecMonitor.startProgressPoller(Duration.ofMillis(200))) {
            final var resources = collectFileResources(itemsToUpload, exporter, progMon) //
                .orElseThrow(HubFailureIOException::new);
            final var uploadJobs =
                submitUploadJobs(exporter, tempFileSupplier, itemsToUpload, resources, progMon, poller);
            return awaitUploads(uploadJobs, progMon::isCanceled);
        }
    }

    private Map<IPath, Future<Result<String, FailureValue>>> submitUploadJobs(
        final WorkflowExporter<CancelationException> exporter, final TempFileSupplier tempFileSupplier,
        final Map<IPath, ItemToUpload> itemsToUpload, final FileResources resources, final IProgressMonitor progMon,
        final ProgressPoller poller) {

        final Map<IPath, Future<Result<String, FailureValue>>> uploadJobs = new LinkedHashMap<>();
        if (itemsToUpload.size() == 1) {
            // only one item is being uploaded, show part uploads as details
            final var pathInTarget = itemsToUpload.keySet().iterator().next();
            final var itemToUpload = itemsToUpload.get(pathInTarget);
            final var itemType = itemToUpload.localItem().type();
            final var title = "Uploading '%s'...".formatted(pathInTarget);
            final var numPartsIfKnown = itemType != ItemType.DATA_FILE
                ? " parts (%s each)".formatted(ConcurrentExecMonitor.bytesToHuman(m_chunkSize))
                : "/%d parts".formatted((resources.totalSize() + m_chunkSize - 1) / m_chunkSize);
            final var splitter = beginMultiProgress(progMon, title, poller, (status, transferRate) -> {
                final var firstLine = "Uploading '%s': %d%s transferred (%.1f%%, %s/sec)" //
                    .formatted(pathInTarget, status.numDone(), numPartsIfKnown, 100.0 * status.totalProgress(),
                        ConcurrentExecMonitor.bytesToHuman(transferRate));
                progMon.setTaskName(firstLine);
                progMon.subTask(status.active().stream() //
                    .map(e -> " \u2022 %s of file part %s".formatted(ConcurrentExecMonitor.percentage(e.getValue()), //
                        e.getKey())) //
                    .collect(Collectors.joining("\n")));
            });

            submitUploadJob(exporter, tempFileSupplier, uploadJobs, pathInTarget, resources, splitter, itemToUpload);
        } else {
            // multiple items to upload, each one is one line of detail
            final var title = "Uploading items...";
            final var splitter = beginMultiProgress(progMon, title, poller, (status, transferRate) -> {
                final var firstLine = "Uploading: %d/%d items transferred (%.1f%%, %s/sec)" //
                    .formatted(status.numDone(), itemsToUpload.size(), 100.0 * status.totalProgress(),
                        ConcurrentExecMonitor.bytesToHuman(transferRate));
                progMon.setTaskName(firstLine);
                progMon.subTask(status.active().stream() //
                    .map(e -> " \u2022 %s of '%s'".formatted(ConcurrentExecMonitor.percentage(e.getValue()),
                        ConcurrentExecMonitor.shortenedPath(e.getKey(), MAX_PATH_LENGTH_IN_MESSAGE))) //
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
                submitUploadJob(exporter, tempFileSupplier, uploadJobs, pathInTarget, resources, subSplitter,
                    itemToUpload);
            }
        }
        return uploadJobs;
    }

    private void submitUploadJob(final WorkflowExporter<CancelationException> exporter,
        final TempFileSupplier tempFileSupplier, final Map<IPath, Future<Result<String, FailureValue>>> uploadJobs,
        final IPath pathInTarget, final FileResources resources, final BranchingExecMonitor subSplitter,
        final ItemToUpload itemToUpload) {
        uploadJobs.put(pathInTarget, HUB_ITEM_TRANSFER_POOL.submit( //
            () -> uploadItem(itemToUpload, exporter, tempFileSupplier, resources.workflowResources(), subSplitter)));
    }

    private static Map<IPath, Result<String, FailureValue>> awaitUploads(
        final Map<IPath, Future<Result<String, FailureValue>>> uploadJobs,
            final BooleanSupplier cancelChecker) throws CancelationException {
        Map<IPath, Result<String, FailureValue>> uploaded = new LinkedHashMap<>();
        var canceled = false;
        for (final var unfinishedJob : uploadJobs.entrySet()) {
            final var path = unfinishedJob.getKey();
            final var future = unfinishedJob.getValue();

            try {
                final var uploadResult = waitForCancellable(future, cancelChecker, throwable -> { // NOSONAR
                    if (throwable instanceof CancelationException cee) {
                        throw cee;
                    } else if (throwable instanceof CouldNotAuthorizeException cnae) {
                        return Result.failure(FailureValue.fromAuthFailure(cnae));
                    } else {
                        LOGGER.atDebug().setCause(throwable) //
                            .addArgument(path) //
                            .log("Unexpected exception during upload job for \"{}\"");
                        return Result.failure(FailureValue.fromUnexpectedThrowable("Upload job failed",
                            List.of("Unexpected exception (%s): %s"
                                .formatted(throwable.getClass().getSimpleName(), throwable.getMessage())),
                            throwable));
                    }
                });
                uploaded.put(path, uploadResult);
            } catch (CancelationException cee) { // NOSONAR
                // we continue to collect results to we can clean up already finished downloads
                canceled = true;
            }
        }
        if (canceled) {
            throw new CancelationException();
        }
        return uploaded;
    }

    private static Result<FileResources, FailureValue> collectFileResources(
        final Map<IPath, ItemToUpload> itemsToUpload, final WorkflowExporter<CancelationException> exporter,
        final IProgressMonitor progMon) throws CancelationException {

        progMon.setTaskName("Preparing items for upload");
        final Map<String, ResourcesToCopy> workflowResources = new LinkedHashMap<>();
        final Map<String, Long> sizes = new LinkedHashMap<>();

        var totalSize = 0L;
        for (final var item : itemsToUpload.entrySet()) {
            if (progMon.isCanceled()) {
                throw new CancelationException();
            }
            final var itemToUpload = item.getValue();
            final var pathStr = itemToUpload.path();
            final var localItem = itemToUpload.localItem();
            final var fsPath = localItem.fsPath();
            final var type = localItem.type();

            final long size;
            try {
                if (type == ItemType.WORKFLOW_GROUP) {
                    size = 0L;
                } else if (type == ItemType.WORKFLOW_LIKE) {
                    final var resources = exporter.collectResourcesToCopy(Set.of(fsPath), fsPath.getParent());
                    workflowResources.put(pathStr, resources);
                    size = resources.numBytes();
                } else {
                    size = Files.size(fsPath);
                }
            } catch (final IOException ioe) {
                return Result.failure(FailureValue.fromUnexpectedThrowable("Failed to upload item(s)",
                    List.of("Could not read file size: " + ioe.getMessage()), ioe));
            }

            sizes.put(pathStr, size);
            totalSize += size;
        }

        return Result.success(new FileResources(workflowResources, sizes, totalSize));
    }

    private Result<String, FailureValue> uploadItem(final ItemToUpload itemToUpload,
        final WorkflowExporter<CancelationException> exporter, final TempFileSupplier tempFileSupplier,
        final Map<String, ResourcesToCopy> workflowResources, final BranchingExecMonitor splitter)
        throws CancelationException {

        final var path = itemToUpload.path();
        final ItemUploadInstructions uploadInstructions = itemToUpload.uploadInstructions();

        final Result<Void, FailureValue> uploadResult;
        if (itemToUpload.localItem().type() == ItemType.DATA_FILE) {
            uploadResult = uploadDataFile(path, itemToUpload.localItem().fsPath(), uploadInstructions, splitter);
        } else {
            uploadResult = uploadWorkflowLike(path, exporter, tempFileSupplier, workflowResources.get(path),
                uploadInstructions, splitter);
        }

        if (!uploadResult.successful()) {
            return uploadResult.asFailure();
        }

        final var result = awaitUploadProcessed(m_catalogClient, m_clientHeaders, path,
            uploadInstructions.getUploadId(), -1, splitter.cancelChecker());

        if (!(result instanceof Success<UploadStatus, ?> success)) {
            return result.asFailure();
        }

        final UploadStatus finalStatus = success.value();
        return switch (finalStatus.getStatus()) {
            case COMPLETED -> Result.success(finalStatus.getStatusMessage());
            case ANALYSIS_PENDING, PREPARED -> throw new IllegalStateException(
                "Stopped polling upload early even though no timeout was provided");
            case ABORTED -> Result.failure(FailureValue.withDetails(FailureType.UPLOAD_ABORTED_BY_HUB,
                "Upload processing aborted", "Response from Hub: " + finalStatus.getStatusMessage()));
            case FAILED -> Result.failure(FailureValue.withDetails(FailureType.UPLOAD_PROCESSING_FAILED,
                "Upload processing failed", "Response from Hub: " + finalStatus.getStatusMessage()));
        };
    }

    static Result<UploadStatus, FailureValue> awaitUploadProcessed(final CatalogServiceClient catalogClient,
        final Map<String, String> clientHeaders, final String path, final String uploadId, final long timeoutMillis,
        final BooleanSupplier cancelChecker) throws CancelationException {

        final Set<UploadStatus.StatusEnum> endStates = EnumSet.of(UploadStatus.StatusEnum.COMPLETED,
            UploadStatus.StatusEnum.ABORTED, UploadStatus.StatusEnum.FAILED);

        try {
            final Result<UploadStatus, FailureValue> result = poll(timeoutMillis, new PollingCallable<>() { // NOSONAR cancel check must be between this and the `return`
                @Override
                public ApiResponse<UploadStatus> poll() throws HubFailureIOException {
                    return catalogClient.pollUploadStatus(uploadId, clientHeaders);
                }

                @Override
                public boolean accept(final UploadStatus state) {
                    LOGGER.atDebug() //
                    .addArgument(path) //
                    .addArgument(state.getStatus()) //
                    .addArgument(state.getStatusMessage()) //
                    .setMessage("Polling state of uploaded item '{}': {}, '{}'") //
                    .log();
                    return cancelChecker.getAsBoolean() || endStates.contains(state.getStatus());
                }
            });

            if (cancelChecker.getAsBoolean()) {
                throw new CancelationException();
            }

            return result;

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelationException(e);
        }
    }

    private Result<Void, FailureValue> uploadWorkflowLike(final String path,
        final WorkflowExporter<CancelationException> exporter, final TempFileSupplier tempFileSupplier,
        final ResourcesToCopy resources, final ItemUploadInstructions instructions, final BranchingExecMonitor splitter)
        throws CancelationException {

        // remember all temp files so they can be deleted cleaned up `finally` if something went wrong
        final List<Path> tempFiles = new ArrayList<>();

        final Deque<Future<Result<Pair<Integer, EntityTag>, FailureValue>>> pendingUploads = new ArrayDeque<>();
        try {
            final var partSupplier = new UploadPartSupplier(m_catalogClient, m_clientHeaders, instructions);
            final var currentWriteProgress = new AtomicDouble();
            try (final var outStream = uploadingOutputStream(path, partSupplier, tempFileSupplier,
                pendingUploads::addLast, tempFiles, currentWriteProgress::get, splitter)) {
                exporter.exportInto(resources, outStream, progress -> {
                    splitter.checkCanceled();
                    currentWriteProgress.set(progress);
                });
            } catch (final HubFailureIOException ex) { // NOSONAR
                return ex.asFailure();
            } catch (final IOException ex) {
                return Result.failure(
                    FailureValue.fromThrowable(FailureType.UPLOAD_OF_WORKFLOW_FAILED, "Failed to upload workflow",
                        List.of("Upload of '%s' failed: %s".formatted(path, ex.getMessage())), ex));
            }

            final var uploadResult =
                finishUpload(path, instructions.getUploadId(), pendingUploads, splitter.cancelChecker());
            if (uploadResult.successful()) {
                // everything went well, no need to delete the files
                tempFiles.clear();
            }
            return uploadResult;
        } finally {
            for (final var tempFile : tempFiles) {
                FileUtils.deleteQuietly(tempFile.toFile());
            }
        }
    }

    private Result<Void, FailureValue> uploadDataFile(final String path, final Path dataFile,
        final ItemUploadInstructions instructions, final BranchingExecMonitor splitter) throws CancelationException {

        final long numBytes;
        try {
            numBytes = Files.size(dataFile);
        } catch (final IOException ioe) {
            return Result.failure(FailureValue.fromUnexpectedThrowable("Failed to upload file",
                List.of("Could not read file size: " + ioe.getMessage()), ioe));
        }

        final Deque<Future<Result<Pair<Integer, EntityTag>, FailureValue>>> pendingUploads = new ArrayDeque<>();
        final var partSupplier = new UploadPartSupplier(m_catalogClient, m_clientHeaders, instructions);

        // do/while because we always want to create at least one chunk
        long pos = 0;
        var partNumber = 1;
        do {
            final var chunkSize = Math.min(m_chunkSize, numBytes - pos);
            final var maxProg = numBytes == 0 ? 0 : (1.0 * chunkSize / numBytes);
            final LeafExecMonitor subMonitor = splitter.createLeafChild(Integer.toString(partNumber), maxProg);
            final var offsetInFile = pos;
            final var partNo = partNumber;

            pendingUploads.add(m_filePartUploader.uploadDataFilePart(path, partNo, partSupplier, dataFile,
                offsetInFile, chunkSize, subMonitor));
            pos += chunkSize;
            partNumber++;
        } while (pos < numBytes);

        return finishUpload(path, instructions.getUploadId(), pendingUploads, splitter.cancelChecker());
    }

    private Result<Void, FailureValue> finishUpload(final String path, final String uploadId,
        final Deque<Future<Result<Pair<Integer, EntityTag>, FailureValue>>> pendingUploads,
        final BooleanSupplier cancelChecker) throws CancelationException {

        var success = false;
        try {
            // removes finished jobs from `pendingUploads`
            final Result<SortedMap<Integer, String>, FailureValue> finishedParts =
                awaitPartsFinished(pendingUploads, cancelChecker);
            if (!(finishedParts instanceof Success<SortedMap<Integer, String>, ?> partsSuccess)) {
                return finishedParts.asFailure();
            }

            try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                final var response =
                        m_catalogClient.reportUploadFinished(uploadId, partsSuccess.value(), m_clientHeaders);
                final Result<Void, ProblemDescription> result = response.result();
                success = result.successful();
                return result.mapFailure(problem -> FailureValue.fromRFC9457(FailureType.UPLOAD_FINISHING_FAILED,
                    response.statusCode(), response.headers(), problem));
            } catch (final HubFailureIOException ex) { // NOSONAR failure is propagated
                return ex.asFailure();
            }
        } finally {
            if (!success) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.atDebug() //
                        .addArgument(path) //
                        .log("Upload of '{}' has failed, cancelling parts and notifying Catalog Service");
                }

                // the upload didn't finish successfully, cancel pending part uploads and delete all temp files
                pendingUploads.forEach(pending -> pending.cancel(true));

                try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                    m_catalogClient.cancelUpload(uploadId, m_clientHeaders);
                } catch (final HubFailureIOException ex) {
                    LOGGER.atError() //
                        .setCause(ex) //
                        .log("Exception while cancelling Hub upload");
                }
            }
        }
    }

    /**
     * Awaits the completion of the part uploads.
     *
     * @param pendingUploads the queue of pending uploads
     * @param cancelChecker supplies a cancellation checker
     * @return map of finished parts with part number as key and entity tag as value
     *
     * @throws IOException if an I/O error occurred during the part upload
     * @throws CancelationException if the part upload was cancelled
     */
    private static Result<SortedMap<Integer, String>, FailureValue> awaitPartsFinished(
        final Deque<Future<Result<Pair<Integer, EntityTag>, FailureValue>>> pendingUploads,
        final BooleanSupplier cancelChecker) throws CancelationException {

        final SortedMap<Integer, String> finished = new TreeMap<>();
        while (!pendingUploads.isEmpty()) {
            final var result = waitForCancellable(pendingUploads.getFirst(), cancelChecker,
                throwable -> Result.failure(FailureValue.fromUnexpectedThrowable("Failed to upload item",
                    List.of("Unexpected error while uploading item: " + throwable.getMessage()), throwable)));

            // only remove after the future has finished, otherwise it can't be cleaned up
            pendingUploads.removeFirst();

            if (!(result instanceof Success<Pair<Integer,EntityTag>, ?> success)) {
                return result.asFailure();
            }

            final var partNoAndETag = success.value();
            final var partNo = partNoAndETag.getLeft();
            final var eTag = partNoAndETag.getRight();
            finished.put(partNo, eTagToString(eTag).orElse(null));
        }

        return Result.success(finished);
    }

    /**
     * Awaits the completion of the part upload.
     *
     * @param pendingUploads the pending uploads
     * @return finished part with part number as key and entity tag as value
     */
    static Result<Pair<Integer, EntityTag>, FailureValue>
        awaitPartFinished(final Future<Result<Pair<Integer, EntityTag>, FailureValue>> pendingUpload) {
        try {
            return waitForCancellable(pendingUpload, () -> false,
                throwable -> Result.failure(FailureValue.fromUnexpectedThrowable("Failed to upload item",
                    List.of("Unexpected error while uploading item: " + throwable.getMessage()), throwable)));
        } catch (CancelationException ex) {
            // can't be canceled, so this would be a bug
            throw new IllegalStateException("Upload was canceled unexpectedly", ex);
        }
    }

    /**
     * Request a multi-file upload of the items described in the given map.
     *
     * @param parentId ID of the workflow group to upload into
     * @param manifest upload manifest
     * @param eTag expected entity tag for the parent group, may be {@code null}
     * @return mapping from relative path to file upload instructions, or {@link Optional#empty()} if the parent
     *         workflow group has changed
     * @throws HubFailureIOException if an I/O error occurred
     */
    static Result<Optional<UploadStarted>, FailureValue> initiateUpload(final CatalogServiceClient catalogClient,
        final Map<String, String> additionalHeaders, final ItemID parentId, final UploadManifest manifest,
        final EntityTag eTag) throws HubFailureIOException {

        final Map<String, String> headers = new HashMap<>(additionalHeaders);
        eTagToString(eTag).ifPresent(eTagStr -> headers.put(HttpHeaders.IF_MATCH, eTagStr));

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response =
                catalogClient.initiateUpload(parentId.id(), manifest, SLOW_OPERATION_READ_TIMEOUT, headers);
            if (response.statusCode() == Status.PRECONDITION_FAILED.getStatusCode()) {
                return Result.success(Optional.empty());
            } else if (response.result() instanceof Success<UploadStarted, ?> success) {
                return Result.success(Optional.of(success.value()));
            } else {
                final var problem = response.result().asFailure().failure();
                throw new HubFailureIOException(FailureValue.fromRFC9457(FailureType.UPLOAD_INITIATION_FAILED,
                    response.statusCode(), response.headers(), problem));
            }
        }
    }

    private @Owning ChunkingFileOutputStream uploadingOutputStream(final String path,
        final UploadPartSupplier partSupplier, final TempFileSupplier tempFileSupplier,
        final Consumer<Future<Result<Pair<Integer, EntityTag>, FailureValue>>> partUploads, final List<Path> chunks,
        final DoubleSupplier currentWriteProgress, final BranchingExecMonitor splitter) {

        return new ChunkingFileOutputStream(m_chunkSize, DigestUtils.getMd5Digest()) {

            private final BranchingExecMonitor m_splitProgress = splitter;

            private double m_progress;

            @Override
            public Path newOutputFile() throws IOException {
                return tempFileSupplier.createTempFile("KNIMEWorkflowUpload", ".knwf.part", true);
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

    /**
     * Supplies additional upload parts to the upload process
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    static final class UploadPartSupplier implements UploadTargetFetcher {

        private final String m_uploadId;

        private final Map<Integer, UploadTarget> m_initialParts;

        private final CatalogServiceClient m_client;

        private final Map<String, String> m_clientHeaders;

        /**
         * Creates a supplier for additional upload parts
         *
         * @param client catalog client
         * @param clientHeaders additional headers for the client
         * @param instructions {@link ItemUploadInstructions}
         */
        public UploadPartSupplier(final CatalogServiceClient client, final Map<String, String> clientHeaders,
            final ItemUploadInstructions instructions) {
            m_client = client;
            m_clientHeaders = clientHeaders;
            m_uploadId = instructions.getUploadId();
            m_initialParts = new LinkedHashMap<>(instructions.getParts().orElse(Map.of()));
        }

        @Override
        public UploadTarget fetch(final int partNo) throws IOException {
            // get and remove initial part, a new one will be requested if this one didn't work
            final var initial = m_initialParts.remove(partNo);
            if (initial != null) {
                return initial;
            }

            try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                final var response = m_client.requestPartUpload(m_uploadId, partNo, m_clientHeaders);
                if (response.result() instanceof Success<UploadTarget, ?> success) {
                    return success.value();
                }

                throw new HubFailureIOException(FailureValue.fromRFC9457(FailureType.UPLOAD_PART_REQUEST_FAILED,
                    response.statusCode(), response.headers(), response.result().asFailure().failure()));
            }
        }
    }
}
