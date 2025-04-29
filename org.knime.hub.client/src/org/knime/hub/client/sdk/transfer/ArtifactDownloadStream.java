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
 *   Apr 29, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.knime.hub.client.sdk.transfer.AsyncHubUploadStream.AsyncUploadStreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;

/**
 * Provides an artifact download stream for single item download from a hub instance.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@SuppressWarnings("java:S4929") // FiterInputStream has sufficient read(int) implementation.
public final class ArtifactDownloadStream extends FilterInputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactDownloadStream.class);

    private @Owning Response m_response;
    private OptionalLong m_contentLength;
    private Optional<EntityTag> m_etag;
    private Map<String, List<Object>> m_responseHeaders;

    private ArtifactDownloadStream(final @Owning InputStream downloadStream,
        final ArtifactDownloadStreamBuilder builder) {
        super(downloadStream);
        m_response = builder.m_response;
        m_contentLength = builder.m_contentLength;
        m_etag = builder.m_etag;
        m_responseHeaders = builder.m_responseHeaders;
    }

    /**
     * Creates a new {@link AsyncUploadStreamBuilder}
     *
     * @return {@link AsyncUploadStreamBuilder}
     */
    public static ArtifactDownloadStreamBuilder builder() {
        return new ArtifactDownloadStreamBuilder();
    }

    /**
     * Artifact download stream builder
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    public static final class ArtifactDownloadStreamBuilder {

        private CatalogServiceClientWrapper m_catalogClient;
        private @NotOwning CatalogServiceClient m_hubClient;
        private Map<String, String> m_additionalHeaders = new HashMap<>();

        private String m_itemId;
        private ItemVersion m_version;

        private @NotOwning Response m_response; // closed by ArtifactDownloadStream
        private OptionalLong m_contentLength;
        private Optional<EntityTag> m_etag;
        private Map<String, List<Object>> m_responseHeaders;

        private ArtifactDownloadStreamBuilder() {
        }

        /**
         * Adds the item ID.
         *
         * @param itemId the ID of the item to download
         * @return {@link ArtifactDownloadStreamBuilder}
         */
        public ArtifactDownloadStreamBuilder withItemId(final String itemId) {
            m_itemId = itemId;
            return this;
        }

        /**
         * Adds the item version.
         *
         * @param version the {@link ItemVersion} of the item to download
         * @return {@link ArtifactDownloadStreamBuilder}
         */
        public ArtifactDownloadStreamBuilder withItemVersion(final ItemVersion version) {
            m_version = version;
            return this;
        }

        /**
         * Adds the {@link CatalogClient}, can't be null.
         *
         * @param catalogClient {@link CatalogClient}
         * @return {@link ArtifactDownloadStreamBuilder}
         */
        public ArtifactDownloadStreamBuilder withCatalogClient(final CatalogServiceClient catalogClient) {
            m_hubClient = catalogClient;
            return this;
        }

        /**
         * Adds additional headers to the requests of the artifact download.
         *
         * @param headerMap the map of headers
         * @return {@link ArtifactDownloadStreamBuilder}
         */
        public ArtifactDownloadStreamBuilder withHeaders(final Map<String, String> headerMap) {
            if (headerMap != null) {
                m_additionalHeaders.putAll(headerMap);
            }
            return this;
        }

        /**
         * Creates an artifact download stream to the hub.
         *
         * @return an {@link ArtifactDownloadStream}
         *
         * @throws IOException if an I/O error occurred during the download
         * @throws CouldNotAuthorizeException if the authenticator has lost connection
         * @throws CancelationException if the process got cancelled
         */
        public @Owning ArtifactDownloadStream build()
                throws IOException, CouldNotAuthorizeException, CancelationException {
            CheckUtils.checkArgumentNotNull(m_hubClient);
            CheckUtils.checkArgumentNotNull(m_itemId);

            m_catalogClient = new CatalogServiceClientWrapper(m_hubClient, m_additionalHeaders);

            // prepare the artifact download
            LOGGER.atDebug() //
                .addArgument(m_itemId) //
                .log("Prpearing download of item with ID: {}");
            final var preparedDownload = m_catalogClient.prepareItemDownlaod(new ItemID(m_itemId), m_version);

            final var downloadId = preparedDownload.getDownloadId();

            URL downloadUrl;
            try {
                // poll the download status until its ready
                downloadUrl = awaitReadyDownloadState(m_itemId, downloadId);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(
                    "Download of item with ID: '%s' has been interrupted while waiting for Hub".formatted(m_itemId));
            }

            // retrieve the download stream
            m_response = m_catalogClient.downloadItemResponse(downloadUrl);
            m_contentLength = OptionalLong.of(m_response.getLength());
            m_etag = Optional.ofNullable(m_response.getEntityTag());
            m_responseHeaders = m_response.getHeaders();

            final var downloadStream = m_response.readEntity(InputStream.class);
            CheckUtils.checkArgument(downloadStream != null, "Expected existing input stream");
            return new ArtifactDownloadStream(downloadStream, this);
        }

        private URL awaitReadyDownloadState(final String itemId, final String downloadId)
                throws IOException, CouldNotAuthorizeException, InterruptedException {
            final var t0 = System.currentTimeMillis();
            for (var numberOfStatusPolls = 0L;; numberOfStatusPolls++) {
                final var state = m_catalogClient.pollDownloadState(downloadId);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.atDebug() //
                        .addArgument(itemId) //
                        .addArgument(state.getStatus()) //
                        .addArgument(state.getStatusMessage()) //
                        .setMessage("Polling state of download item with ID '{}': {}, '{}'") //
                        .log();
                }

                switch (state.getStatus()) {
                    case ABORTED:
                        throw new IOException(state.getStatusMessage());
                    case PREPARING, ZIPPING:
                        break;
                    case READY:
                        return state.getDownloadUrl().orElseThrow();
                    case FAILED:
                        throw new IOException(state.getStatusMessage());
                }

                // Sequence: 200ms, 400ms, 600ms, 800ms and then 1s until the timeout is reached
                Thread.sleep(numberOfStatusPolls < 4 ? (200 * (numberOfStatusPolls + 1)) : 1_000);

                final long elapsed = System.currentTimeMillis() - t0;
                if (elapsed > CatalogServiceClientWrapper.DOWNLOAD_STATUS_POLL_TIMEOUT.toMillis()) {
                    throw new IOException("Download was not ready within %.1fs".formatted(elapsed / 1000.0));
                }
            }
        }

    }

    @Override
    public void close() throws IOException {
        super.close();
        m_response.close();
    }

    /**
     * Retrieves the content length if available.
     *
     * @return {@link OptionalLong}
     */
    public OptionalLong getContentLength() {
        return m_contentLength;
    }

    /**
     * Retrieves the entity tag of the response.
     *
     * @return {@link EntityTag}
     */
    public Optional<EntityTag> getEntityTag() {
        return m_etag;
    }

    /**
     * Retrieves the response headers of the successful download request.
     *
     * @return the response headers
     */
    public Map<String, List<Object>> getHeaders() {
        return m_responseHeaders;
    }

}
