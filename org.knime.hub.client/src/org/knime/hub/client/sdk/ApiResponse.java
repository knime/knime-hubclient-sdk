/*
 * ------------------------------------------------------------------------
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
 * -------------------------------------------------------------------
 *
 * History
 *   Nov 6, 2024 (magnus): created
 */

package org.knime.hub.client.sdk;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.knime.hub.client.sdk.Result.Success;

import jakarta.ws.rs.core.EntityTag;

/**
 * API response class which combines response details from {@link ApiClient} and
 * response entities from the API class.
 *
 * @param headers       the response headers
 * @param statusCode    the response status code
 * @param statusMessage the response status message
 * @param etag          the response etag
 * @param result        {@link Result} the response result, only a {@link Success} in case of 2XX response codes
 * @param <R>           the response type
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@SuppressWarnings("java:S1105") // curly brace location, seems to be buggy
public record ApiResponse<R> (
        Map<String, List<Object>> headers,
        int statusCode,
        String statusMessage,
        Optional<EntityTag> etag,
        Result<R, FailureValue> result) {

    /**
     * Checks that the given response signals success (via a 2XX HTTP status code).
     *
     * @return the response body
     * @throws HubFailureIOException if the request was unsuccessful
     */
    public R checkSuccessful() throws HubFailureIOException {
        return checkSuccessful(msg -> msg);
    }

    /**
     * Checks that the given response signals success (via a 2XX HTTP status code).
     *
     * @param messageCallback callback for modifying the error message, receiving the message from the response
     * @return the response body
     * @throws HubFailureIOException if the request was unsuccessful
     */
    public R checkSuccessful(final UnaryOperator<String> messageCallback) throws HubFailureIOException {
        if (result instanceof Result.Success<R, FailureValue> success) {
            return success.value();
        }
        final var failure = result.asFailure().failure();
        throw new HubFailureIOException(messageCallback.apply(failure.title()), failure);
    }
}
