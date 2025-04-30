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
 *   Apr 22, 2025 (magnus): created
 */
package org.knime.hub.client.sdk;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.hub.client.sdk.ent.ProblemDescription;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ResponseProcessingException;

/**
 * Record describing the supported request failure values.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 *
 * @param type failure type
 * @param status HTTP status, {@code -1} if not applicable
 * @param title error title for the failure category
 * @param details list of details (user-oriented explanation/hint)
 * @param cause throwable cause of the failure, {@code null} if not applicable
 */
public record FailureValue ( //
    FailureType type,
    int status,
    String title, //
    List<String> details, //
    Throwable cause) {

    /**
     * Creates a failure value from an {@code application/problem+json} Hub response.
     *
     * @param type failure type
     * @param status HTTP status
     * @param problem problem description
     * @return failure value
     */
    public static FailureValue fromRFC9457(final FailureType type, final int status, final ProblemDescription problem) {
        return new FailureValue(type, status, problem.getTitle(), problem.getDetails(), null);
    }

    /**
     * Creates a failure value with the given type and title.
     *
     * @param type failure type
     * @param title problem title
     * @return failure value
     */
    public static FailureValue withTitle(final FailureType type, final String title) {
        return new FailureValue(type, -1, title, List.of(), null);
    }

    /**
     * Creates a failure value for an unexpected {@link Throwable}.
     *
     * @param title problem title
     * @param thrw throwable cause
     * @return failure value
     */
    public static FailureValue fromUnexpectedThrowable(final String title, final Throwable thrw) {
        return new FailureValue(FailureType.UNEXPECTED_ERROR, -1, title, List.of(), thrw);
    }

    /**
     * Creates a failure value for an expected {@link Throwable}.
     *
     * @param type failure type
     * @param title problem title
     * @param thrw throwable cause
     * @return failure value
     */
    public static FailureValue fromThrowable(final FailureType type, final String title, final Throwable thrw) {
        return new FailureValue(type, -1, title, List.of(), thrw);
    }

    /**
     * Creates a failure value for a connectivity-related {@link Throwable}.
     *
     * @param thrw throwable cause
     * @return failure value
     */
    public static FailureValue forConnectivityProblem(final Throwable thrw) {
        final var message = thrw.getMessage();
        final var title = "Network connectivity problem" + (StringUtils.isBlank(message) ? "" : (": " + message));
        return new FailureValue(FailureType.NETWORK_CONNECTIVITY_ERROR, -1, title, List.of(), thrw);
    }

    /**
     * Creates a failure value for a non-successful HTTP request.
     *
     * @param type failure type
     * @param status HTTP status
     * @param title failure title
     * @return failure value
     */
    public static FailureValue fromHTTP(final FailureType type, final int status, final String title) {
        return new FailureValue(type, status, title, List.of(), null);
    }

    /**
     * Creates a failure value for a logged-out authenticator.
     *
     * @param cnae cause
     * @return failure value
     */
    public static FailureValue fromAuthFailure(final CouldNotAuthorizeException cnae) {
        return new FailureValue(FailureType.COULD_NOT_AUTHORIZE, -1, "Network connectivity problem", List.of(), cnae);
    }

    /**
     * Creates a failure value for problem processing an HTTP request or response.
     *
     * @param e processing exception
     * @return failure value
     */
    public static FailureValue fromProcessingException(final ProcessingException e) {
        final var cause = e.getCause();
        if (cause instanceof SocketException || cause instanceof SocketTimeoutException) {
            return forConnectivityProblem(cause);
        }

        final var prefix =
            "Error while processing " + (e instanceof ResponseProcessingException ? "response" : "request");
        final var message = StringUtils.getIfBlank(e.getMessage(), cause::getMessage);
        return fromUnexpectedThrowable(prefix + (StringUtils.isBlank(message) ? "" : (": " + message)), e.getCause());
    }
}
