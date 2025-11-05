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
 *   4 Sept 2025 (leonard.woerteler): created
 */
package org.knime.hub.client.sdk.util;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.FailureType;
import org.knime.hub.client.sdk.FailureValue;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.ent.ProblemDescription;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;

/**
 * Utility methods for handling JAX-RS {@link Response}s.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 * @since 0.2
 */
public final class ResponseUtils {

    private ResponseUtils() {
        // utility class
    }

    /**
     * Converts a non-successful {@link Response} into a {@link ProblemDescription} or throws a
     * {@link HubFailureIOException} if the response could not be interpreted.
     *
     * @param response the response to convert
     * @return the problem description
     * @throws HubFailureIOException if the response could not be interpreted
     */
    public static ProblemDescription toProblemDescription(final Response response) throws HubFailureIOException {
        final var status = response.getStatusInfo();
        return switch (status.getFamily()) {
            case SUCCESSFUL -> throw new IllegalStateException("Unexpected successful response");
            case REDIRECTION -> toRedirectExceptionOrFailureDescription(response);
            case CLIENT_ERROR, SERVER_ERROR -> toFailureDescription(response);
            case INFORMATIONAL, OTHER -> throw new IllegalStateException("Unexpected HTTP response code: %d %s"
                .formatted(status.getStatusCode(), status.getReasonPhrase()));
        };
    }

    private static ProblemDescription toFailureDescription(final Response response)
        throws HubFailureIOException {
        final var contentType = response.getMediaType();
        if (contentType != null && contentType.isCompatible(ApiClient.APPLICATION_PROBLEM_JSON_TYPE)
                && response.hasEntity()) {
            try {
                return response.readEntity(ProblemDescription.class);
            } catch (final ProcessingException pe) {
                throw new HubFailureIOException(
                    FailureValue.fromProcessingException("Failed to read Hub response", pe));
            }
        }

        // Create a exception failure value
        final var responseStatusInfo = response.getStatusInfo();
        final var message = StringUtils.getIfBlank(response.hasEntity() ? response.readEntity(String.class) : null,
            responseStatusInfo::getReasonPhrase);
        return new ProblemDescription(null, response.getStatus(), "Hub request failed", null,
            List.of("Error: " + message), null);
    }

    private static ProblemDescription toRedirectExceptionOrFailureDescription(final Response response)
        throws HubFailureIOException {
        // A "304 Not Modified" response is the only exemption to redirect exceptions,
        // since it does not indicate that an auto-redirect failed
        if (response.getStatus() == Response.Status.NOT_MODIFIED.getStatusCode()) {
            return toFailureDescription(response);
        }

        // A redirect is not a failure response, so we don't expect `application/problem+json` here
        final String message;
        if (response instanceof org.apache.cxf.jaxrs.impl.ResponseImpl cxfResponse) {
            final var location = cxfResponse.getOutMessage().get("transport.retransmit.url");
            message = "Redirect failed (firewall?): '%s'".formatted(location);
        } else {
            message = StringUtils.getIfBlank(response.hasEntity() ? response.readEntity(String.class) :
                null, () -> response.getStatusInfo().getReasonPhrase());
        }
        throw new HubFailureIOException(new FailureValue(FailureType.REDIRECT_FAILED, response.getStatus(),
            response.getHeaders(), "HTTP redirect failed", List.of(message), null));
    }
}
