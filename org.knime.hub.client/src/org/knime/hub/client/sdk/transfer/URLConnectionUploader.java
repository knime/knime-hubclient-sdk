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
 *   7 May 2025 (leonard.woerteler): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.transfer.FilePartUploader.StreamingUploader;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.ext.RuntimeDelegate;
import jakarta.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

public final class URLConnectionUploader implements StreamingUploader {

    private static final HeaderDelegate<EntityTag> ETAG_DELEGATE =
        RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);

    static final StreamingUploader INSTANCE = new URLConnectionUploader();

    private URLConnectionUploader() {
    }

    @Override
    public EntityTag performUpload(final URL targetUrl, final String httpMethod,
        final Map<String, List<String>> httpHeaders, final @Owning InputStream contentStream, final long contentLength,
        final LongConsumer writtenBytesAdder, final BooleanSupplier cancelChecker)
        throws CancelationException, IOException {

        HttpURLConnection connection = null;
        try (contentStream) {
            connection = prepareConnection(targetUrl, httpMethod, httpHeaders, contentLength);

            try (final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
                try (final var out = connection.getOutputStream()) {
                    transferContent(contentStream, out, writtenBytesAdder, cancelChecker);
                }

                final var statusInfo = Response.Status.fromStatusCode(connection.getResponseCode());
                if (statusInfo.getFamily() == Family.SUCCESSFUL) {
                    return ETAG_DELEGATE.fromString(connection.getHeaderField(HttpHeaders.ETAG));
                }

                try (final var errStream = connection.getErrorStream()) {
                    final var message = errStream != null ? new String(errStream.readAllBytes(), StandardCharsets.UTF_8)
                        : statusInfo.getReasonPhrase();
                    throw HttpExceptionUtils.wrapException(statusInfo.getStatusCode(),
                        "Failed to upload artifact: " + message);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection prepareConnection(final URL targetUrl, final String httpMethod,
        final Map<String, List<String>> httpHeaders, final long contentLength) throws IOException {
        HttpURLConnection connection;
        connection = (HttpURLConnection)URLConnectionFactory.getConnection(targetUrl);
        connection.setRequestMethod(httpMethod);
        for (final var header : httpHeaders.entrySet()) {
            for (final var value : header.getValue()) {
                connection.addRequestProperty(header.getKey(), value);
            }
        }
        connection.setFixedLengthStreamingMode(contentLength);
        connection.setDoOutput(true);
        return connection;
    }

    /**
     * Prepares an HTTP connection for the given request URL.
     *
     * @param url               the request URL
     * @param httpMethod        the HTTP request method
     * @param clientHeaders     the client headers
     * @param chunkSize         the chunk size
     * @param connectionTimeout the connection timeout
     * @param readTimeout       the read timeout
     *
     * @return the {@link HttpURLConnection}
     *
     * @throws IOException if an I/O error occurred during opening of the connection
     */
    public static HttpURLConnection prepareConnection(final URL url, final String httpMethod,
        final Map<String, String> clientHeaders, final int chunkSize, final Duration connectionTimeout,
        final Duration readTimeout) throws IOException {
        final var conn = (HttpURLConnection) URLConnectionFactory.getConnection(url);
        conn.setRequestMethod(httpMethod);
        conn.setDoOutput(true);
        conn.setConnectTimeout(Math.toIntExact(connectionTimeout.toMillis()));
        conn.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
        clientHeaders.forEach(conn::addRequestProperty);
        conn.setChunkedStreamingMode(chunkSize);
        return conn;
    }

    private static void transferContent(@NotOwning final InputStream contentStream, final OutputStream out,
        final LongConsumer writtenBytesAdder, final BooleanSupplier cancelChecker)
        throws IOException, CancelationException {
        final var buffer = new byte[64 * (int)FileUtils.ONE_KB];
        for (int read; (read = contentStream.read(buffer, 0, buffer.length)) >= 0;) {
            if (cancelChecker.getAsBoolean()) {
                throw new CancelationException();
            }
            out.write(buffer, 0, read);
            writtenBytesAdder.accept(read);
        }
    }
}
