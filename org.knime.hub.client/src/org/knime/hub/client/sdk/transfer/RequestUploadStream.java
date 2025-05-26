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
 *   Jun 6, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.hub.client.sdk.ApiClient.Method;
import org.knime.hub.client.sdk.FailureType;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.api.CatalogServiceClient;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

/**
 * Provides an request upload stream for single item upload to a hub instance.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.2
 */
public final class RequestUploadStream extends OutputStream {

    private final @Owning OutputStream m_uploadStream;

    private final HttpURLConnection m_connection;

    private boolean m_isCancelled;

    /** Chunk size for uploading (10MB). */
    private static final int CHUNK_SIZE = (int)(10 * FileUtils.ONE_MB);

    /**
     * Opens an {@link RequestUploadStream} to upload an item.
     *
     * @param catalogClient the catalog client
     * @param path the path to the repository item
     * @param clientHeaders the client headers for Hub API calls
     * @param contentType The content type of the uploaded item
     *
     * @return The opened stream
     *
     * @throws HubFailureIOException If the download stream couldn't be opened or created
     */
    public static @Owning RequestUploadStream create(final CatalogServiceClient catalogClient,
        final IPath path, final MediaType contentType, final Map<String, String> clientHeaders)
        throws HubFailureIOException {
        CheckUtils.checkArgumentNotNull(catalogClient);
        CheckUtils.checkArgumentNotNull(path);
        CheckUtils.checkArgumentNotNull(contentType);

        try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var urlConnection = catalogClient.getApiClient().createApiRequest() //
                    .withContentTypeHeader(contentType) //
                    .withHeaders(clientHeaders) //
                    .createAPIURLConnection(Method.PUT.name(), path, CHUNK_SIZE);
            return openUploadStream(urlConnection);
        } catch (IOException ex) {
            throw new HubFailureIOException(
                FailureValue.fromThrowable(FailureType.UPLOAD_CONNECTION_CREATION_FAILED,
                    "Could not create upload connection", List.of(ex.getMessage()), ex));
        }
    }

    private static @Owning RequestUploadStream openUploadStream(final @Owning HttpURLConnection connection)
        throws HubFailureIOException {
        try {
            connection.connect();
            return new RequestUploadStream(connection,
                new BufferedOutputStream(connection.getOutputStream()));
        } catch (IOException ex) {
            throw new HubFailureIOException(
                FailureValue.fromThrowable(FailureType.UPLOAD_STREAM_CREATION_FAILED,
                    "Could not create upload stream", List.of(ex.getMessage()), ex));
        }
    }

    private RequestUploadStream(final @Owning HttpURLConnection connection,
        final @Owning OutputStream outputStream) {
        m_connection = connection;
        m_uploadStream = outputStream;
    }

    /**
     * Cancel the upload.
     */
    public void cancel() {
        m_connection.disconnect();
        m_isCancelled = true;
    }

    @Override
    public void write(final int b) throws IOException {
        try {
            m_uploadStream.write(b);
        } catch (IOException ex) {
            if (m_connection != null) {
                m_connection.disconnect();
            }
            throw ex;
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        try {
            m_uploadStream.write(b, off, len);
        } catch (IOException ex) {
            if (m_connection != null) {
                m_connection.disconnect();
            }
            throw ex;
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        try {
            m_uploadStream.write(b);
        } catch (IOException ex) {
            if (m_connection != null) {
                m_connection.disconnect();
            }
            throw ex;
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (!m_isCancelled) {
            m_uploadStream.close();
            final var statusInfo = Response.Status.fromStatusCode(m_connection.getResponseCode());
            if (statusInfo.getFamily() != Family.SUCCESSFUL) {
                try (final var errStream = m_connection.getErrorStream()) {
                    final var message = errStream != null ? new String(errStream.readAllBytes(), StandardCharsets.UTF_8)
                        : statusInfo.getReasonPhrase();
                    throw HttpExceptionUtils.wrapException(statusInfo.getStatusCode(),
                        "Failed to upload item: " + message);
                } finally {
                    m_connection.disconnect();
                }
            }
            m_connection.disconnect();
        }
    }

}
