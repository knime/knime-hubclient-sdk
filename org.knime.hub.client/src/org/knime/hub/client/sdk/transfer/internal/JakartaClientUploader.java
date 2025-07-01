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
package org.knime.hub.client.sdk.transfer.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.hub.client.sdk.CancelationException;
import org.knime.hub.client.sdk.transfer.FilePartUploader.StreamingUploader;
import org.knime.hub.client.sdk.transfer.MonitoringInputStream;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status.Family;

class JakartaClientUploader implements StreamingUploader {

    private final Function<String, Builder> m_invocationBuilderSupplier;

    JakartaClientUploader(final Function<String, Invocation.Builder> invocationBuilderSupplier) {
        m_invocationBuilderSupplier = invocationBuilderSupplier;
    }

    @Override
    public EntityTag performUpload(final URL targetUrl, final String httpMethod,
        final Map<String, List<String>> httpHeaders, final @Owning InputStream contentStream, final long contentLength,
        final LongConsumer writtenBytesAdder, final BooleanSupplier cancelChecker)
        throws CancelationException, IOException {

        final var url = targetUrl.toString();
        final var builder = m_invocationBuilderSupplier.apply(url);
        builder.header(HttpHeaders.CONTENT_LENGTH, Long.toString(contentLength));

        final var impl = (org.apache.cxf.jaxrs.client.spec.InvocationBuilderImpl)builder;
        impl.getWebClient().getConfiguration().getHttpConduit().getClient().setAllowChunking(false);

        try (contentStream;
                final var supp = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups();
                final var wrappedIn = new ReportingInputStream(contentStream, writtenBytesAdder, cancelChecker);
                final var response =
                    builder.method(httpMethod, Entity.entity(wrappedIn, MediaType.APPLICATION_OCTET_STREAM_TYPE))) {

            if (cancelChecker.getAsBoolean()) {
                throw new CancelationException();
            }

            final var statusInfo = response.getStatusInfo();
            if (statusInfo.getFamily() == Family.SUCCESSFUL) {
                return response.getEntityTag();
            }

            // Create a exception failure value
            final var message = StringUtils.getIfBlank(response.hasEntity() ?
                response.readEntity(String.class) : null, statusInfo::getReasonPhrase);
            throw HttpExceptionUtils.wrapException(statusInfo.getStatusCode(), "Failed to upload artifact: " + message);

        } catch (final ProcessingException pe) {
            throw pe.getCause() instanceof IOException ioe ? ioe : new IOException(pe.getMessage(), pe);
        }
    }

    private static final class ReportingInputStream extends MonitoringInputStream {
        private final LongConsumer m_writtenBytesAdder;
        private final BooleanSupplier m_cancelChecker;

        private ReportingInputStream(final InputStream wrapped, final LongConsumer writtenBytesAdder,
            final BooleanSupplier cancelChecker) {
            super(wrapped);
            m_writtenBytesAdder = writtenBytesAdder;
            m_cancelChecker = cancelChecker;
        }

        @Override
        protected boolean isCanceled() {
            return m_cancelChecker.getAsBoolean();
        }

        @Override
        protected void addBytesRead(final int numBytes) {
            m_writtenBytesAdder.accept(numBytes);
        }
    }
}
