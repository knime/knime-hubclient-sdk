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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.api.CatalogServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.Response.StatusType;

/**
 * Provides an artifact download stream for single item download from a hub instance.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public final class ArtifactDownloadStream extends FilterInputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactDownloadStream.class);

    private @Owning Response m_response;
    private final long m_contentLength;
    private final Map<String, List<Object>> m_responseHeaders;

    /**
     * Opens an {@link ArtifactDownloadStream} for the given artifact in the given version.
     * @param catalogClient catalog client for initiating the stream
     * @param headers headers for Hub API calls
     * @param itemId ID of the item to download
     * @param version version of the item to download
     *
     * @return the opened stream
     * @throws IOException if the download stream couldn't be opened
     * @throws CouldNotAuthorizeException if a call to a Hub API could not be authorized
     */
    public static @Owning ArtifactDownloadStream create(final CatalogServiceClient catalogClient,
        final Map<String, String> headers, final ItemID itemId, final ItemVersion version)
        throws IOException, CouldNotAuthorizeException {
        CheckUtils.checkArgumentNotNull(catalogClient);
        CheckUtils.checkArgumentNotNull(itemId);

        // prepare the artifact download
        LOGGER.atDebug() //
            .addArgument(itemId.id()) //
            .log("Preparing download of item with ID: {}");

        final String downloadId;
        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var response = catalogClient.preparedDownload(itemId.id(), version, headers);
            downloadId = response.checkSuccessful().getDownloadId();
        }

        final URL downloadUrl;
        try {
            // poll the download status until it's ready
            downloadUrl = CatalogServiceUtils.awaitDownloadReady(catalogClient, headers, downloadId, state -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.atDebug() //
                        .addArgument(itemId.id()) //
                        .addArgument(state.getStatus()) //
                        .addArgument(state.getStatusMessage()) //
                        .setMessage("Polling state of download item with ID '{}': {}, '{}'") //
                        .log();
                }
            });
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                "Download of item with ID: '%s' has been interrupted while waiting for Hub".formatted(itemId.id()));
        }

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var invocationBuilder = catalogClient.getApiClient().nonApiInvocationBuilder(downloadUrl.toString());
            return openDownloadStream(invocationBuilder.get());
        }
    }

    private static @Owning ArtifactDownloadStream openDownloadStream(final @Owning Response response)
            throws ResourceAccessException {
        final StatusType statusInfo = response.getStatusInfo();
        if (statusInfo.getFamily() != Family.SUCCESSFUL) {
            try (response) {
                var reason = Optional.ofNullable(statusInfo.getReasonPhrase()) //
                        .orElse(statusInfo.toEnum().getReasonPhrase());
                final String errContent = response.hasEntity() ? response.readEntity(String.class) : "";
                throw HttpExceptionUtils.wrapException(statusInfo.getStatusCode(),
                    "Could not open download stream to %s: %s".formatted(response,
                        errContent.isBlank() ? reason : (reason + ": " + errContent)));
            }
        }
        return new ArtifactDownloadStream(response);
    }

    private ArtifactDownloadStream(final @Owning Response response) {
        super(response.readEntity(InputStream.class));
        m_response = response;
        m_contentLength = response.getLength();
        m_responseHeaders = response.getHeaders();
    }

    /**
     * Retrieves the content length if available.
     *
     * @return {@link OptionalLong}
     */
    public OptionalLong getContentLength() {
        return m_contentLength < 0 ? OptionalLong.empty() : OptionalLong.of(m_contentLength);
    }

    /**
     * Retrieves the response headers of the successful download request.
     *
     * @return the response headers
     */
    public Map<String, List<Object>> getHeaders() {
        return m_responseHeaders;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            m_response.close();
        }
    }
}
