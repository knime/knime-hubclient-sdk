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

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NotOwning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.ClassUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.FailureType;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.Result;
import org.knime.hub.client.sdk.Result.Failure;
import org.knime.hub.client.sdk.Result.Success;
import org.knime.hub.client.sdk.api.HubClientAPI;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloader for sets of items from a Hub.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public final class HubDownloader extends AbstractHubTransfer {

    /**
     * Reference to an item in the Hub repository.
     *
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
     * Reference to a downloaded Hub item.
     *
     * @param item description of the item on the Hub
     * @param pathInTarget path below the download root to download to
     * @param id the id to download from
     * @param size item download size if known
     */
    public record DownloadInfo(HubItem item, IPath pathInTarget, ItemID id, OptionalLong size) {}

    /**
     * Information about Hub items to be downloaded.
     *
     * @param itemsToDownload list of items to download
     * @param numDownloads total number of downloads
     * @param totalSize total size to download if known
     * @param supportsArtifactDownload <code>true</code> if the artifact download is supported
     */
    public record DownloadResources(List<DownloadInfo> itemsToDownload, long numDownloads, OptionalLong totalSize,
        boolean supportsArtifactDownload) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(HubDownloader.class);

    /**
     * @param hubClient Hub API client
     * @param additionalHeaders additional header parameters
     */
    public HubDownloader(final @NotOwning HubClientAPI hubClient, final Map<String, String> additionalHeaders) {
        super(hubClient, additionalHeaders);
    }

    /**
     * Initiates an asynchronous download flow.
     *
     * @param itemIds IDs of the items to download
     * @param progMon progress monitor
     * @return description of all items that have to be downloaded
     * @throws CancelationException if the operation was canceled
     * @throws HubFailureIOException if a request to Hub failed
     */
    public Pair<DownloadResources, Map<IPath, FailureValue>> initiateDownload(final List<ItemID> itemIds, // NOSONAR
            final IProgressMonitor progMon) throws CancelationException, HubFailureIOException {
        progMon.beginTask("Collecting items to download...", itemIds.size());
        final Map<IPath, Pair<HubItem, Result<DownloadInfo, FailureValue>>> results = new LinkedHashMap<>();
        var optTotalSize = 0L;
        var supportsArtifactDownload = false;

        final Map<IPath, FailureValue> notDownloadable = new LinkedHashMap<>();
        if (!itemIds.isEmpty()) {
            // get the common parent (we expect all items to stem from the same group)
            final RepositoryItem firstParent = deepListParent(itemIds.get(0), progMon::isCanceled) //
                .orElseThrow(HubFailureIOException::new) //
                .item();

            // check if artifact download is supported
            supportsArtifactDownload = supportsArtifactDownload(firstParent.getMasonControls());

            final Map<String, RepositoryItem> deepListedItems;
            if (itemIds.size() == 1) {
                final var itemId = itemIds.get(0);
                final RepositoryItem item = deepListItem(itemId, progMon::isCanceled) //
                    .orElseThrow(HubFailureIOException::new) //
                    .item();
                deepListedItems = Map.of(item.getId(), item);
            } else {
                // we cache the first item's siblings, since all items are expected to stem from the same group
                deepListedItems = ClassUtils.castStream(WorkflowGroup.class, firstParent) //
                    .flatMap(group -> group.getChildren().stream()) //
                    .collect(Collectors.toMap(ch -> ch.getId(), Function.identity()));
            }

            for (final var itemId : itemIds) {
                var repositoryItem = deepListedItems.get(itemId.id());
                if (repositoryItem == null) {
                    final var message = "Item '%s' could not be found".formatted(itemId.id());
                    notDownloadable.put(IPath.forPosix(itemId.id()),
                        FailureValue.withTitle(FailureType.DOWNLOAD_ITEM_NOT_FOUND, message));
                } else if (!firstParent.getMasonControls().containsKey(DOWNLOAD)) {
                    final var message = "Item at '" + repositoryItem.getPath() + "' cannot be downloaded.";
                    notDownloadable.put(IPath.forPosix(itemId.id()),
                        FailureValue.withTitle(FailureType.DOWNLOAD_ITEM_NOT_DOWNLOADABLE, message));
                } else {
                    final var rootPath = IPath.forPosix(repositoryItem.getPath());
                    progMon.subTask("Analyzing '%s'".formatted(
                        ConcurrentExecMonitor.shortenedPath(rootPath.toString(), MAX_PATH_LENGTH_IN_MESSAGE)));
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
            if (result instanceof Success<DownloadInfo, FailureValue> success) {
                itemsToDownload.add(success.value());
                if (!hubItem.isFolder()) {
                    numDownloads++;
                }
            } else {
                notDownloadable.put(targetPath, ((Failure<?, FailureValue>)result).failure());
            }
        }
        final var totalSize = optTotalSize < 0 ? OptionalLong.empty() : OptionalLong.of(optTotalSize);
        return Pair.of(new DownloadResources(itemsToDownload, numDownloads, totalSize, supportsArtifactDownload),
            notDownloadable);
    }

    private static boolean supportsArtifactDownload(final Map<String, Control> spaceParentControls) {
        return spaceParentControls.containsKey(INITIATE_DOWNLOAD);
    }

    /**
     * Performs the download.
     *
     * @param resources resources to download
     * @param tempFileSupplier supplier for temp files
     * @param progMon progress monitor
     * @return mapping from item ID to the type and result (success or failure) of the download
     * @throws CancelationException if the download was canceled
     */
    public Map<IPath, Pair<HubItem, Result<Optional<Path>, FailureValue>>> download(final DownloadResources resources,
            final TempFileSupplier tempFileSupplier, final IProgressMonitor progMon) throws CancelationException {

        final Map<IPath, Result<Optional<Path>, FailureValue>> downloaded;
        try (final var poller = ConcurrentExecMonitor.startProgressPoller(Duration.ofMillis(200))) {
            final var splitter = beginMultiProgress(progMon, "Downloading items...", poller, (status, transferRate) -> {
                final var firstLine = "Downloading: %d/%d items transferred (%.1f%%, %s/sec)" //
                        .formatted(status.numDone(), resources.itemsToDownload().size(), 100.0 * status.totalProgress(),
                            ConcurrentExecMonitor.bytesToHuman(transferRate));
                progMon.setTaskName(firstLine);
                progMon.subTask(status.active().stream() //
                    .map(e -> " \u2022 %s of '%s'".formatted(ConcurrentExecMonitor.percentage(e.getValue()),
                        ConcurrentExecMonitor.shortenedPath(e.getKey(), MAX_PATH_LENGTH_IN_MESSAGE))) //
                    .collect(Collectors.joining("\n")));
            });
            final var totalSize = resources.totalSize();
            final var downloadJobs =
                submitDownloadJobs(resources.supportsArtifactDownload(), resources.itemsToDownload(),
                    totalSize.isPresent(), tempFileSupplier, totalSize.orElse(resources.numDownloads()), splitter);

            downloaded = awaitDownloads(downloadJobs, progMon::isCanceled);
        }

        final Map<IPath, Pair<HubItem, Result<Optional<Path>, FailureValue>>> out = new LinkedHashMap<>();
        for (final var download : resources.itemsToDownload()) {
            final var hubItem = download.item();
            final IPath pathInTarget = download.pathInTarget();
            final Result<Optional<Path>, FailureValue> result = hubItem.isFolder() ? Result.success(Optional.empty()) //
                : CheckUtils.checkNotNull(downloaded.get(pathInTarget));
            out.put(pathInTarget, Pair.of(hubItem, result));
        }
        return out;
    }

    private Map<IPath, Future<Result<Path, FailureValue>>> submitDownloadJobs(final boolean supportsArtifactDownload,
        final List<DownloadInfo> downloadInfos, final boolean allSizesKnown, final TempFileSupplier tempFileSupplier,
        final double maxProgress, final BranchingExecMonitor splitter) {
        final var downloadJobs = new LinkedHashMap<IPath, Future<Result<Path, FailureValue>>>();
        for (final var download : downloadInfos) {
            final var hubItem = download.item();
            if (!hubItem.isFolder()) {
                final var itemSize = allSizesKnown ? download.size().getAsLong() : 1;
                final var monitor = splitter.createLeafChild(hubItem.path().toString(), itemSize / maxProgress);
                downloadJobs.put(download.pathInTarget(), HUB_ITEM_TRANSFER_POOL.submit( //
                    new DownloadItemTask(download, tempFileSupplier, monitor, supportsArtifactDownload)));
            }
        }
        return downloadJobs;
    }

    private static Map<IPath, Result<Optional<Path>, FailureValue>> awaitDownloads(
            final Map<IPath, Future<Result<Path, FailureValue>>> downloadJobs, final BooleanSupplier cancelChecker)
            throws CancelationException {

        Map<IPath, Result<Optional<Path>, FailureValue>> downloaded = new LinkedHashMap<>();
        try {
            var canceled = false;
            for (final var unfinishedJob : downloadJobs.entrySet()) {
                final var pathInTarget = unfinishedJob.getKey();

                final ErrorHandler<Path> errorHandler = thrw -> {
                    if (thrw instanceof CancelationException ce) {
                        throw ce;
                    } else if (thrw instanceof CouldNotAuthorizeException cnae) {
                        return Result.failure(FailureValue.fromAuthFailure(cnae));
                    } else {
                        return Result.failure(FailureValue.fromUnexpectedThrowable(
                            "Failed to download item '" + pathInTarget + "': " + thrw.getMessage(), thrw));
                    }
                };

                final Future<Result<Path, FailureValue>> downloadFuture = unfinishedJob.getValue();
                try {
                    // wrap the path in `Optional.of(...)`, folders are represented by `Optional.empty()`
                    final Result<Path, FailureValue> downloadResult =
                        waitForCancellable(downloadFuture, cancelChecker, errorHandler);
                    downloaded.put(pathInTarget, downloadResult.map(Optional::of));
                } catch (CancelationException cee) { // NOSONAR
                    // we continue to collect results to we can clean up already finished downloads
                    canceled = true;
                }
            }
            if (canceled) {
                throw new CancelationException();
            }

            final Map<IPath, Result<Optional<Path>, FailureValue>> out = downloaded;
            downloaded = null;
            return out;
        } finally {
            if (downloaded != null) {
                cleanUpDownloads(downloaded);
            }
        }
    }

    private static void cleanUpDownloads(final Map<IPath, Result<Optional<Path>, FailureValue>> downloaded) {
        for (final var res : downloaded.values()) {
            if (res instanceof Success<Optional<Path>, FailureValue> success) {
                final var path = success.value();
                final var file = path.orElseThrow();
                LOGGER.atDebug() //
                    .addArgument(file) //
                    .log("Cleaning up downloaded item from temp location \"{}\"");
                try {
                    Files.deleteIfExists(file);
                } catch (final IOException e) {
                    LOGGER.atDebug() //
                        .addArgument(file) //
                        .setCause(e) //
                        .log("Failed to delete item at temp location \"{}\"");
                }
            }
        }
    }

    private final class DownloadItemTask implements Callable<Result<Path, FailureValue>> {

        private final DownloadInfo m_download;
        private final TempFileSupplier m_tempFileSupplier;
        private final LeafExecMonitor m_monitor;
        private final boolean m_supportsArtifactDownload;

        DownloadItemTask(final DownloadInfo download, final TempFileSupplier tempFileSupplier,
            final LeafExecMonitor monitor, final boolean supportsArtifactDownload) {
            m_download = download;
            m_tempFileSupplier = tempFileSupplier;
            m_monitor = monitor;
            m_supportsArtifactDownload = supportsArtifactDownload;
        }

        @Override
        public Result<Path, FailureValue> call() throws CancelationException, CouldNotAuthorizeException {
            // set a very small non-zero value to signal that the download job has started
            m_monitor.setProgress(Double.MIN_VALUE);
            final AtomicReference<Path> tempFile;
            try {
                tempFile = new AtomicReference<>(m_tempFileSupplier.createTempFile("KNIMEHubItem", ".download", false));
            } catch (final IOException ex) {
                return Result.failure(
                    FailureValue.fromThrowable(FailureType.UNEXPECTED_ERROR, "Could not create temporary file", ex));
            }

            final var hubItem = m_download.item();
            try {
                LOGGER.atDebug() //
                    .addArgument(hubItem.path()) //
                    .addArgument(tempFile.get()) //
                    .log("Downloading '{}' into file '{}'");

                try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                    return m_supportsArtifactDownload ? performArtifactDownload(tempFile)
                        : performDownload(tempFile);
                } catch (SocketException | SocketTimeoutException ex) {
                    return Result.failure(FailureValue.forConnectivityProblem(ex));
                } catch (HubFailureIOException ex) { // NOSONAR
                    return ex.asFailure();
                } catch (IOException ex) {
                    return Result.failure(
                        FailureValue.fromThrowable(FailureType.UNEXPECTED_ERROR, "Failed to download item", ex));
                }
            } finally {
                LOGGER.atDebug() //
                    .addArgument(hubItem.path()) //
                    .log("Ended downloading '{}'");
                m_monitor.done();
                final var remainingAfterFailure = tempFile.get();
                if (remainingAfterFailure != null) {
                    FileUtils.deleteQuietly(remainingAfterFailure.toFile());
                }
            }
        }

        private Result<Path, FailureValue> performDownload(final AtomicReference<Path> tempFile)
            throws IOException, CancelationException {
            final var response = m_catalogClient.downloadItemById(m_download.id().id(), null, null,
                m_clientHeaders, (in, contentLength) -> {
                    // prefer size from the HTTP request if available, fall back to Catalog information otherwise
                    final var numBytes = contentLength.orElse(m_download.size().orElse(-1));
                    writeStreamToFileWithProgress(in, numBytes, tempFile.get(), m_monitor);
                    return tempFile.getAndSet(null);
                });
            return response.result();
        }

        private Result<Path, FailureValue> performArtifactDownload(final AtomicReference<Path> tempFile)
            throws IOException, CancelationException {
            try (final var downloadStream = m_catalogClient.createArtifactDownloadStream(m_download.id(), null,
                m_clientHeaders, m_monitor.cancelChecker())) {
                // prefer size from the HTTP request if available, fall back to Catalog information otherwise
                final var numBytes = downloadStream.getContentLength().orElse(m_download.size().orElse(-1));
                writeStreamToFileWithProgress(downloadStream, numBytes, tempFile.get(), m_monitor);
                return Result.success(tempFile.getAndSet(null));
            }
        }
    }

    private static long collectItems(final RepositoryItem repositoryItem, final IPath rootPath,
            final Map<IPath, Pair<HubItem, Result<DownloadInfo, FailureValue>>> results) {
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
                itemId, size < 0 ? OptionalLong.empty() : OptionalLong.of(size)))));
        } else if (repositoryItem instanceof Workflow || repositoryItem instanceof Component) {
            results.put(targetPath, Pair.of(hubItem, Result.success(new DownloadInfo(hubItem, targetPath,
                itemId, size < 0 ? OptionalLong.empty() : OptionalLong.of(size)))));
        } else {
            final var message = "Unexpected item type at '" + itemPath + "': " + repositoryItem.getType();
            results.put(targetPath, Pair.of(hubItem,
                Result.failure(FailureValue.withTitle(FailureType.DOWNLOAD_ITEM_UNKNOWN_TYPE, message))));
            return 0L;
        }
        return size;
    }

    private static long addIfKnown(final long optA, final long optB) {
        return Math.min(optA, optB) < 0 ? -1 : (optA + optB);
    }

    private static long getSize(final RepositoryItem item) {
        return item instanceof Sized s ? s.getSize() : -1;
    }
}
