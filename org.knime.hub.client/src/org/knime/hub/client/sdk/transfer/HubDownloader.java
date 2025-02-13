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
 *   Created on Jun 10, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.ClassUtils;
import org.knime.core.util.FileUtil;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.ent.Component;
import org.knime.hub.client.sdk.ent.Control;
import org.knime.hub.client.sdk.ent.Data;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.RepositoryItem.RepositoryItemType;
import org.knime.hub.client.sdk.ent.Sized;
import org.knime.hub.client.sdk.ent.Workflow;
import org.knime.hub.client.sdk.ent.WorkflowGroup;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.BranchingExecMonitor;
import org.knime.hub.client.sdk.transfer.ConcurrentExecMonitor.LeafExecMonitor;
import org.knime.hub.client.sdk.Result.Failure;
import org.knime.hub.client.sdk.Result.Success;
import org.knime.hub.client.sdk.api.HubClientAPI;

import com.knime.enterprise.server.rest.api.KnimeRelations;

/**
 * Downloader for sets of items from a Hub.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public final class HubDownloader extends AbstractHubTransfer {

    /**
     * @param id the item's ID in the Hub's repository
     * @param type the item's type
     * @param path the item's path in the Hub's file system
     */
    public record HubItem(ItemID id, RepositoryItem.RepositoryItemType type, IPath path) {
        /**
         * @return {@code true} if this represents a folder-like item (folder or space), {@code false} otherwise
         */
        public boolean isFolder() {
            return type == RepositoryItemType.WORKFLOW_GROUP || type == RepositoryItemType.SPACE;
        }
    }

    /**
     * @param item description of the item on the Hub
     * @param pathInTarget path below the download root to download to
     * @param path Path to download from
     * @param size item download size if known
     */
    public record DownloadInfo(HubItem item, IPath pathInTarget, IPath path, OptionalLong size) {}

    /**
     * @param itemsToDownload list of items to download
     * @param numDownloads total number of downloads
     * @param totalSize total size to download if known
     */
    public record DownloadResources(List<DownloadInfo> itemsToDownload, long numDownloads, OptionalLong totalSize) {}

    private static final NodeLogger LOGGER = NodeLogger.getLogger(HubDownloader.class);

    /**
     * @param hubClient Hub API client
     * @param authenticator Hub authenticator
     * @param connectTimeout connect timeout for HTTP connections
     * @param readTimeout read timeout for HTTP connections
     */
    public HubDownloader(final HubClientAPI hubClient, final Map<String, String> apHeaders) {
        super(hubClient, apHeaders);
    }

    /**
     * Initiates an asynchronous download flow.
     *
     * @param itemIds IDs of the items to download
     * @param progMon progress monitor
     * @return description of all items that have to be downloaded
     * @throws ResourceAccessException if a request to Hub failed
     * @throws CanceledExecutionException if the operation was canceled
     */
    public Pair<DownloadResources, Map<IPath, Failure<Void>>> initiateDownload(final List<ItemID> itemIds, // NOSONAR
            final IProgressMonitor progMon) throws ResourceAccessException, CanceledExecutionException {
        progMon.beginTask("Collecting items to download...", itemIds.size());
        final Map<IPath, Pair<HubItem, Result<DownloadInfo>>> results = new LinkedHashMap<>();
        var optTotalSize = 0L;

        final Map<IPath, Failure<Void>> notDownloadable = new LinkedHashMap<>();
        if (!itemIds.isEmpty()) {
            // get the common parent (we expect all items to stem from the same group)
            final RepositoryItem firstParent = deepListParent(itemIds.get(0), progMon::isCanceled).getLeft();
            
            // if the common parent is not the account group we need to fetch the download control from the parent space
            final var isAccountGroup = IPath.forPosix(firstParent.getPath()).segmentCount() == 2;
            Map<String, Control> parentSpaceControls = new HashMap<>();
            if (!isAccountGroup) {
                parentSpaceControls = getMasonControlsOfSpaceParent(itemIds.get(0));
            }
            
            final Map<String, RepositoryItem> deepListedItems;
            if (itemIds.size() == 1) {
                final var itemId = itemIds.get(0);
                deepListedItems = Map.of(itemId.id(),
                    deepListItem(itemId, progMon::isCanceled).orElseThrow().getLeft());
            } else {
                // we cache the first item's siblings, since all items are expected to stem from the same group
                deepListedItems = ClassUtils.castStream(WorkflowGroup.class, firstParent) //
                    .flatMap(group -> group.getChildren().stream()) //
                    .filter(ch -> {
                        if (ch.getId() == null) {
                            throw new NoSuchElementException();
                        }
                        return true;
                    })
                    .collect(Collectors.toMap(ch -> ch.getId(), Function.identity()));
            }

            for (final var itemId : itemIds) {
                var repositoryItem = deepListedItems.get(itemId.id());
                if (repositoryItem == null) {
                    notDownloadable.put(IPath.forPosix(itemId.id()),
                        Result.failure("Item '%s' could not be found", null));
                } else if (RepositoryItemType.SPACE == repositoryItem.getType() && 
                        !repositoryItem.getMasonControls().containsKey(KnimeRelations.DOWNLOAD.toString())) {
                    notDownloadable.put(IPath.forPosix(itemId.id()),
                            Result.failure("Item at '" + repositoryItem.getPath() + "' cannot be downloaded.", null));
                } else if (!isAccountGroup && !parentSpaceControls.containsKey(KnimeRelations.DOWNLOAD.toString())) {
                    notDownloadable.put(IPath.forPosix(itemId.id()),
                            Result.failure("Item at '" + repositoryItem.getPath() + "' cannot be downloaded.", null));
                } else {
                    final var rootPath = IPath.forPosix(repositoryItem.getPath());
                    progMon.subTask("Analyzing '%s'".formatted(shortenedPath(rootPath.toString())));
                    final var parent = rootPath.segmentCount() == 0 ? rootPath : rootPath.removeLastSegments(1);
                    optTotalSize = addIfKnown(optTotalSize, collectItems(repositoryItem, parent, results));
                    progMon.worked(1);
                }
            }
        }

        final List<DownloadInfo> itemsToDownload = new ArrayList<>();
        var numDownloads = 0;
        for (final var e : results.entrySet()) {
            final var targetPath = e.getKey();
            final var itemAndResult = e.getValue();
            final var hubItem = itemAndResult.getLeft();
            final var result = itemAndResult.getRight();
            if (result instanceof Success<DownloadInfo> success) {
                itemsToDownload.add(success.value());
                if (!hubItem.isFolder()) {
                    numDownloads++;
                }
            } else {
                notDownloadable.put(targetPath, ((Failure<?>)result).coerceResultType());
            }
        }
        final var totalSize = optTotalSize < 0 ? OptionalLong.empty() : OptionalLong.of(optTotalSize);
        return Pair.of(new DownloadResources(itemsToDownload, numDownloads, totalSize), notDownloadable);
    }

    /**
     * Performs the download.
     *
     * @param resources resources to download
     * @param progMon progress monitor
     * @return mapping from item ID to the type and result (success or failure) of the download
     * @throws CanceledExecutionException if the download was canceled
     */
    public Map<IPath, Pair<HubItem, Result<Optional<Path>>>> download(final DownloadResources resources,
            final IProgressMonitor progMon) throws CanceledExecutionException {
        final var splitter = beginMultiProgress(progMon, "Downloading items...", status -> {
            final var firstLine = "Downloading: %d/%d items transferred (%.1f%%, %s/sec)" //
                .formatted(status.numDone(), resources.itemsToDownload().size(), 100.0 * status.totalProgress(),
                    bytesToHuman(status.bytesPerSecond()));
            progMon.setTaskName(firstLine);
            progMon.subTask(status.active().stream() //
                .map(e -> " \u2022 %s of '%s'".formatted(percentage(e.getValue()),
                    StringUtils.abbreviateMiddle(e.getKey(), "...", MAX_PATH_LENGTH_IN_MESSAGE))) //
                .collect(Collectors.joining("\n")));
        });
        final var totalSize = resources.totalSize();
        final var downloadJobs = submitDownloadJobs(resources.itemsToDownload(), totalSize.isPresent(),
            totalSize.orElse(resources.numDownloads()), splitter);

        final Map<IPath, Result<Optional<Path>>> downloaded = awaitDownloads(downloadJobs, progMon::isCanceled);

        final Map<IPath, Pair<HubItem, Result<Optional<Path>>>> out = new LinkedHashMap<>();
        for (final var download : resources.itemsToDownload()) {
            final var hubItem = download.item();
            final IPath pathInTarget = download.pathInTarget();
            final Result<Optional<Path>> result = hubItem.isFolder() ? Result.success(Optional.empty()) //
                : CheckUtils.checkNotNull(downloaded.get(pathInTarget));
            out.put(pathInTarget, Pair.of(hubItem, result));
        }
        return out;
    }

    private Map<IPath, Future<Result<Path>>> submitDownloadJobs(final List<DownloadInfo> downloadInfos,
            final boolean allSizesKnown, final double maxProgress, final BranchingExecMonitor splitter) {
        final var downloadJobs = new LinkedHashMap<IPath, Future<Result<Path>>>();
        for (final var download : downloadInfos) {
            final var hubItem = download.item();
            if (!hubItem.isFolder()) {
                final var itemSize = allSizesKnown ? download.size().getAsLong() : 1;
                final var contribution = itemSize / maxProgress;
                downloadJobs.put(download.pathInTarget(), HUB_ITEM_TRANSFER_POOL.submit( //
                    () -> downloadItemTask(download,
                        splitter.createLeafChild(hubItem.path().toString(), contribution))));
            }
        }
        return downloadJobs;
    }

    private static Map<IPath, Result<Optional<Path>>> awaitDownloads(
            final Map<IPath, Future<Result<Path>>> downloadJobs, final BooleanSupplier cancelChecker)
            throws CanceledExecutionException {

        Map<IPath, Result<Optional<Path>>> downloaded = new LinkedHashMap<>();
        try {
            var canceled = false;
            for (final var unfinishedJob : downloadJobs.entrySet()) {
                final var pathInTarget = unfinishedJob.getKey();
                final var future = unfinishedJob.getValue();

                try {
                    final var downloadResult = waitForCancellable(future, cancelChecker, throwable -> {
                        if (throwable instanceof CanceledExecutionException cee) { // NOSONAR
                            throw cee;
                        } else {
                            return Result.failure(throwable.getMessage(), throwable);
                        }
                    });

                    // wrap the path into an `Optional`
                    downloaded.put(pathInTarget, downloadResult.map(Optional::of));
                } catch (CanceledExecutionException cee) { // NOSONAR
                    // we continue to collect results to we can clean up already finished downloads
                    canceled = true;
                }
            }
            if (canceled) {
                throw new CanceledExecutionException();
            }

            final Map<IPath, Result<Optional<Path>>> out = downloaded;
            downloaded = null;
            return out;
        } finally {
            if (downloaded != null) {
                for (final var res : downloaded.values()) {
                    if (res instanceof Success<Optional<Path>> success) {
                        LOGGER.debug(() -> "Cleaning up downloaded item from temp location '%s'" //
                            .formatted(success.value()));
                        FileUtil.deleteRecursively(success.value().orElseThrow().toFile());
                    }
                }
            }
        }
    }

    private Result<Path> downloadItemTask(final DownloadInfo download, final LeafExecMonitor monitor)
            throws IOException, CanceledExecutionException {
        // set a very small non-zero value to signal that the download job has started
        monitor.setProgress(Double.MIN_VALUE);
        var tempFile = new AtomicReference<>(FileUtil.createTempFile("KNIMEHubItem", ".download", false).toPath());
        try {
            LOGGER.debug(() -> "Downloading '%s' into file '%s'".formatted(download.item().path(), tempFile.get()));
            final var file = m_catalogClient.downloadItem(download.path(), download.item().type(), (in, contentLength) -> { // NOSONAR
                // prefer size from the HTTP request if available, fall back to Catalog information otherwise
                final var numBytes = contentLength.orElse(download.size().orElse(-1));
                final var bufferSize = (int)(FileUtils.ONE_MB / 2);
                try (final var bufferedInStream = new BufferedInputStream(in, bufferSize);
                        final var outStream = Files.newOutputStream(tempFile.get())) {
                    final var buffer = new byte[bufferSize];
                    long written = 0;
                    for (int read; (read = bufferedInStream.read(buffer)) >= 0;) {
                        monitor.checkCanceled();
                        outStream.write(buffer, 0, read);
                        written += read;
                        monitor.addTransferredBytes(read);
                        if (numBytes >= 0) { // NOSONAR
                            monitor.setProgress(Math.min(1.0 * written / numBytes, 1.0));
                        }
                    }
                }
                return tempFile.getAndSet(null);
            });
            return Result.success(file);
        } finally {
            LOGGER.debug(() -> "Ended downloading '%s'".formatted(download.item().path()));
            monitor.done();
            final var remainingAfterFailure = tempFile.get();
            if (remainingAfterFailure != null) {
                Files.deleteIfExists(remainingAfterFailure);
            }
        }
    }

    private static long collectItems(final RepositoryItem repositoryItem, final IPath rootPath,
            final Map<IPath, Pair<HubItem, Result<DownloadInfo>>> results) {
        final var repositoryItemId = repositoryItem.getId();
        if (repositoryItemId == null) {
            throw new NoSuchElementException();
        }
        final var itemId = new ItemID(repositoryItem.getId());
        final var itemPath = IPath.forPosix(repositoryItem.getPath());
        final var hubItem = new HubItem(itemId, repositoryItem.getType(), itemPath);
        final var targetPath = itemPath.makeRelativeTo(rootPath).makeAbsolute();

        if (repositoryItem instanceof WorkflowGroup group) { // also covers spaces
            var totalSize = 0L;
            results.put(targetPath, Pair.of(hubItem,
                Result.success(new DownloadInfo(hubItem, targetPath, null, OptionalLong.empty()))));
            for (final var childItem : Optional.ofNullable(group.getChildren()).orElse(List.of())) {
                totalSize = addIfKnown(totalSize, collectItems(childItem, rootPath, results));
            }
            return totalSize;
        }

        final var size = getSize(repositoryItem);
        if (repositoryItem instanceof Data) {
            results.put(targetPath, Pair.of(hubItem, Result.success(new DownloadInfo(hubItem, targetPath, 
                itemPath, size < 0 ? OptionalLong.empty() : OptionalLong.of(size)))));
        } else if (repositoryItem instanceof Workflow || repositoryItem instanceof Component) {
            results.put(targetPath, Pair.of(hubItem, Result.success(new DownloadInfo(hubItem, targetPath, 
                itemPath, size < 0 ? OptionalLong.empty() : OptionalLong.of(size)))));
        } else {
            results.put(targetPath, Pair.of(hubItem,
                Result.failure("Unexpected item type at '" + itemPath + "': " + repositoryItem.getType(), null)));
            return 0L;
        }
        return size;
    }

    private static long addIfKnown(final long optA, final long optB) {
        return Math.min(optA, optB) < 0 ? -1 : (optA + optB);
    }

    private static long getSize(final RepositoryItem item) {
        if (item instanceof Sized s) {
            return s.getSize();
        } else {
            return -1L;
        }
    }
}
