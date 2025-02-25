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
 *   May 31, 2024 (leonard.woerteler): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.function.FailableBiConsumer;
import org.apache.commons.lang3.function.FailableConsumer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.FileNodePersistor;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodePersistor;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.SingleNodeContainer;
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.core.util.VMFileLocker;

/**
 * Exporter saving one or more items (workflows, templates or data files) as one zipped file.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public final class WorkflowExporter {

    /** Types of items to upload. */
    public enum ItemType {
        /** Workflow-like items, like Workflows, Components and Metanodes. */
        WORKFLOW_LIKE,
        /** Workflow groups (i.e., folders). */
        WORKFLOW_GROUP,
        /** Data files. */
        DATA_FILE
    }

    private static final NodeLogger LOGGER = NodeLogger.getLogger(WorkflowExporter.class);

    private final boolean m_excludeData;

    /**
     * @param excludeData whether execution data should be excluded by the export
     */
    public WorkflowExporter(final boolean excludeData) {
        m_excludeData = excludeData;
    }

    /**
     * Exports the given elements into a file with the given name. All elements must be below the root directory
     * (as in {@link Path#startsWith(Path) element.startsWith(rootDir)}), and the relative path of each element from
     * {@code rootDir} determines its location inside the resulting archive.
     *
     * @param elementsToExport top-level elements to export
     * @param rootDir root directory determining the paths inside the exported archive
     * @param exportFile location of the resulting archive
     * @param exec progress monitor
     * @throws CanceledExecutionException if execution was canceled
     * @throws IOException if an I/O exception occurred
     */
    public void export(final Iterable<Path> elementsToExport, final Path rootDir, final Path exportFile,
            final ExecutionMonitor exec) throws CanceledExecutionException, IOException {
        exec.setMessage("Archiving selected workflows... ");
        // if the data should be excluded from the export iterate over the resources and add only the wanted stuff
        // i.e. the "intern" folder and "*.zip" files are excluded
        final var resources = collectResourcesToCopy(elementsToExport, rootDir);

        // 10% for collecting the files...
        exec.setProgress(0.1);

        // start compressing
        var success = false;
        try (final var outputStream = Files.newOutputStream(exportFile)) {
            final ExecutionMonitor subProgress = exec.createSubProgress(0.9);
            resources.exportInto(outputStream, progress -> {
                subProgress.checkCanceled();
                subProgress.setProgress(progress);
            });
            success = true;
        } finally {
            exec.setProgress(1.0, (String)null);
            if (!success) {
                Files.delete(exportFile);
            }
        }
    }

    /**
     * Scans the elements to export and collects all files and directories that will be archived. If execution data is
     * to be excluded, it will not be path of this method's result.
     *
     * @param elementsToExport top-level items to be exported
     * @param root root directory relative to which the destination path of the resources is determined
     * @return record of items to be copied
     * @throws IOException if files or folders could not be accessed
     */
    public ResourcesToCopy collectResourcesToCopy(final Iterable<Path> elementsToExport, final Path root)
            throws IOException {
        final Map<Path, IPath> resourceList = new LinkedHashMap<>();
        final long[] counts = { 0, 0 };
        for (Path element : elementsToExport) {
            // add all files within the workflow or group
            addResourcesFor(resourceList, root, counts, element);
        }
        return new ResourcesToCopy(resourceList, (int)counts[0], counts[1]);
    }

    /**
     * Determines whether or not the given ZIP file (assumed to be a {@code .knwf} file) has a single top-level
     * folder containing the item's contents.
     *
     * @param zipFile the ZIP file's path
     * @return {@code true} if there is a single top-level folder, {@code false} otherwise
     * @throws IOException I/O-related exception
     */
    public static boolean hasZipSingleRootFolder(final Path zipFile) throws IOException {
        String root = null;
        try (final var zip = ZipFile.builder().setPath(zipFile).get()) {
            for (final var entries = zip.getEntries(); entries.hasMoreElements();) {
                final var entry = entries.nextElement();
                final var entryPath = org.eclipse.core.runtime.Path.forPosix(entry.getName());
                if (entryPath.segmentCount() == 0) {
                    // entry for the root folder, skip
                    continue;
                }

                if (entryPath.segmentCount() == 1 && !entry.isDirectory()) {
                    // top-level file
                    return false;
                }

                final var newRoot = entryPath.segment(0);
                if (root != null && !root.equals(newRoot)) {
                    // found a second top-level folder
                    return false;
                }
                root = newRoot;
            }
        }
        return root != null;
    }

    /**
     * Enumerates all items in the subtree of the given path in a local workspace.
     *
     * @param root root path, may be a single item or a workflow group (folder)
     * @param isValidItem predicate which tests if the item is valid
     * @param visitor called for every item
     * @throws IOException in case of problems during the file system traversal
     * @throws CanceledExecutionException passed through from the visitor
     */
    public static void visitWorkspaceItems(final Path root, final Predicate<Path> isValidItem,
            final FailableBiConsumer<Path, ItemType, CanceledExecutionException> visitor)
            throws IOException, CanceledExecutionException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() { // NOSONAR
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                    throws IOException {
                if (!isValidItem.test(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else if (Files.isRegularFile(dir.resolve(WorkflowPersistor.WORKFLOW_FILE))) {
                    visit(dir, ItemType.WORKFLOW_LIKE);
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    visit(dir, ItemType.WORKFLOW_GROUP);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (isValidItem.test(file)) {
                    visit(file, ItemType.DATA_FILE);
                }
                return FileVisitResult.CONTINUE;
            }

            private void visit(final Path file, final ItemType itemType) {
                try {
                    visitor.accept(file, itemType);
                } catch (CanceledExecutionException e) {
                    // we declare the exception in the surrounding method
                    throw ExceptionUtils.asRuntimeException(e);
                }
            }
        });
    }

    /**
     * Implements the exclude policy. Called only if "exclude data" is checked.
     *
     * @param store the resource to check
     * @return true if the given resource should be excluded, false if it should be included
     */
    private static boolean excludeResource(final Path store) {
        final var name = store.getFileName().toString();
        if (name.equals("internal")) {
            return true;
        }

        final Stream<String> excludedPrefixes;
        if (Files.isDirectory(store)) {
            // directories to exclude:
            excludedPrefixes = Stream.of(FileNodePersistor.PORT_FOLDER_PREFIX,
                FileNodePersistor.INTERNAL_TABLE_FOLDER_PREFIX, FileNodePersistor.FILESTORE_FOLDER_PREFIX,
                NodePersistor.INTERN_FILE_DIR, SingleNodeContainer.DROP_DIR_NAME);
        } else {
            // files to exclude:
            if (name.equals("data.xml")) {
                return true;
            }
            excludedPrefixes = Stream.of("model_", WorkflowPersistor.SAVED_WITH_DATA_FILE, VMFileLocker.LOCK_FILE);
        }

        return excludedPrefixes.anyMatch(name::startsWith);
    }

    /**
     * Collects the files (files only) that are contained in the passed workflow or workflow group and are that are not
     * excluded. For workflows it does include all files contained in sub dirs (unless excluded).
     *
     * @param resources result mapping from local resources to export to target path
     * @param counts
     * @param element the resource representing the thing to export
     * @param excludeData true if KNIME data files should be excluded
     * @throws IOException
     */
    private void addResourcesFor(final Map<Path, IPath> resources, final Path root, final long[] counts,
            final Path element) throws IOException {
        if (!Files.isDirectory(element)) {
            addResource(resources, root, counts, element);
        } else if (Files.isRegularFile(element.resolve(WorkflowPersistor.WORKFLOW_FILE))) {
            // workflows, components and metanodes: add with optionally excluded data
            addRecursively(resources, root, counts, element);
        } else {
            // workflow groups: only add `workflowset.meta` if present
            final var metadata = element.resolve(WorkflowPersistor.METAINFO_FILE);
            if (Files.isRegularFile(metadata)) {
                addResource(resources, root, counts, metadata);
            }
        }
    }

    private void addRecursively(final Map<Path, IPath> resources, final Path root, final long[] counts, final Path dir)
            throws IOException {
        try (final var contents = Files.list(dir)) {
            final Path[] children = contents.toArray(Path[]::new);
            if (children.length == 0) {
                // see AP-13538 (empty dirs are ignored -- so we add them)
                addResource(resources, root, counts, dir);
            }
            for (final var child : children) {
                if (!isMetaNode(child, dir) && m_excludeData && excludeResource(child)) {
                    continue;
                }
                if (Files.isDirectory(child)) {
                    addRecursively(resources, root, counts, child);
                } else if (Files.isRegularFile(child)) {
                    addResource(resources, root, counts, child);
                } else {
                    LOGGER.debug(() -> "Skipping unexpected item '%s' (neither file nor directory)".formatted(child));
                }
            }
        }
    }

    private static void addResource(final Map<Path, IPath> resources, final Path root, final long[] counts,
            final Path child) throws IOException {
        CheckUtils.checkArgument(root == null || child.startsWith(root), //
                "File '%s' is not below the root '%s'", child, root);
        IPath path = org.eclipse.core.runtime.Path.EMPTY;
        for (final var segment : (root == null ? child : root.relativize(child).normalize())) {
            path = path.append(segment.toString());
        }
        if (Files.isDirectory(child)) {
            resources.put(child, path.addTrailingSeparator());
        } else {
            resources.put(child, path);
            counts[0]++;
            counts[1] += Files.size(child);
        }
    }

    /**
     * @param file to check
     * @return true if this is a metanode (or a sub node) in a workflow (or metanode in another metanode, etc.)
     */
    private static boolean isMetaNode(final Path file, final Path parent) {
        return Files.isRegularFile(parent.resolve(WorkflowPersistor.WORKFLOW_FILE)) && Files.isDirectory(file)
                && Files.isRegularFile(file.resolve(WorkflowPersistor.WORKFLOW_FILE));
    }

    /**
     * Record of all resources to be copied into an archive.
     *
     * @param paths mapping from paths of all resources to copy to their respective path inside the archive
     * @param numFiles number of files (as opposed to directories) to copy
     * @param numBytes number of bytes of all files combined
     */
    public record ResourcesToCopy(Map<Path, IPath> paths, int numFiles, long numBytes) {

        /**
         * Exports the resources into the given output stream.
         *
         * @param outputStream stream to write the export archive to, will be closed in the end
         * @param updater progress updater
         * @throws CanceledExecutionException if execution was cancelled
         * @throws IOException if something went wrong with the export
         */
        public void exportInto(final OutputStream outputStream,
                final FailableConsumer<Double, CanceledExecutionException> updater)
                throws CanceledExecutionException, IOException {
            try (final var zipper = new Zipper(outputStream)) {
                final var numBytesWritten = new AtomicLong();
                final FailableConsumer<Long, CanceledExecutionException> subUpdater =
                        add -> updater.accept(1.0 * numBytesWritten.addAndGet(add) / numBytes);
                for (final var file : paths.entrySet()) {
                    zipper.addEntry(file.getKey(), file.getValue(), subUpdater);
                }
            }
        }
    }

    private static final class Zipper implements Closeable {

        private static final int BUFFER_SIZE = 2 * (int)FileUtils.ONE_MB;

        private static final int COMPRESSION_LEVEL = 9;

        private final byte[] m_buffer = new byte[BUFFER_SIZE];

        private @Owning ZipOutputStream m_zipOutStream;

        Zipper(final OutputStream outputStream) {
            m_zipOutStream = new ZipOutputStream(new BufferedOutputStream(outputStream, BUFFER_SIZE));
            m_zipOutStream.setLevel(COMPRESSION_LEVEL);
        }

        void addEntry(final Path source, final IPath destination,
                final FailableConsumer<Long, CanceledExecutionException> updater)
                throws IOException, CanceledExecutionException {
            if (Files.isDirectory(source)) {
                // mostly for empty directories (but non-empty dirs are accepted also)
                m_zipOutStream.putNextEntry(new ZipEntry(destination.addTrailingSeparator().toString()));
                m_zipOutStream.closeEntry();
            } else {
                final var size = Files.size(source);
                if (size == 0) {
                    // this is mainly for the .knimeLock file of open workflows; the file is locked and windows forbids
                    // mmap-ing locked files but FileInputStream seems to mmap files which leads to exceptions while
                    // reading the (non-existing) contents of the file
                    m_zipOutStream.putNextEntry(new ZipEntry(destination.toString()));
                    m_zipOutStream.closeEntry();
                } else {
                    try (final var inStream = new BufferedInputStream(Files.newInputStream(source), BUFFER_SIZE)) {
                        m_zipOutStream.putNextEntry(new ZipEntry(destination.toString()));
                        for (int read; (read = inStream.read(m_buffer)) >= 0;) { //NOSONAR
                            m_zipOutStream.write(m_buffer, 0, read);
                            updater.accept(Long.valueOf(read));
                        }
                    } catch (IOException ioe) {
                        throw new IOException(String.format("Unable to add file \"%s\" to archive: %s",
                            source.toAbsolutePath(), ioe.getMessage()), ioe);
                    } finally {
                        m_zipOutStream.closeEntry();
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            try (final var zipOut = m_zipOutStream) {
                m_zipOutStream = null;
            }
        }
    }
}
