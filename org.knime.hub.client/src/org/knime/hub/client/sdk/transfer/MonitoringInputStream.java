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
 *   5 May 2025 (leonard.woerteler): created
 */
package org.knime.hub.client.sdk.transfer;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jdt.annotation.Owning;

/**
 * Abstract decorator for {@link InputStream}s supporting early return (via {@link #isCanceled()}) and
 * progress reporting (via {@link #addBytesRead(int)}).
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public abstract class MonitoringInputStream extends InputStream {

    private @Owning InputStream m_wrapped;

    /**
     * Constructor taking the input stream to be decorated.
     *
     * @param wrapped stream to be decorated
     */
    protected MonitoringInputStream(final InputStream wrapped) {
        m_wrapped = wrapped;
    }

    /**
     * Checks whether the input stream should stop providing data.
     *
     * @return {@code true} if cancellation was requested, {@code false} otherwise
     */
    protected abstract boolean isCanceled();

    /**
     * Called whenever bytes have been returned to the caller.
     *
     * @param numBytes number of bytes returned
     */
    protected abstract void addBytesRead(final int numBytes);

    @Override
    public final int read() throws IOException {
        if (isCanceled()) {
            return -1;
        }
        final var readByte = m_wrapped.read();
        if (readByte >= 0) {
            addBytesRead(1);
        }
        return readByte;
    }

    @Override
    public final int read(final byte[] b, final int off, final int len) throws IOException {
        if (isCanceled()) {
            return -1;
        }
        final int numBytes = m_wrapped.read(b, off, len);
        if (numBytes >= 0) {
            addBytesRead(numBytes);
        }
        return numBytes;
    }

    @Override
    public void close() throws IOException {
        try (final var wrapped = m_wrapped) {
            m_wrapped = null;
        }
    }
}
