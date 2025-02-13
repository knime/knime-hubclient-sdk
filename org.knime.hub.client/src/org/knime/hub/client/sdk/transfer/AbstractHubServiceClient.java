/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Jun 9, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.EclipseUtil;
import org.knime.core.util.KNIMEServerHostnameVerifier;
import org.knime.core.util.auth.Authenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.exception.HttpExceptionUtils;
import org.knime.core.util.exception.ResourceAccessException;

import com.knime.enterprise.client.rest.RestAuthenticator;
import com.knime.enterprise.server.rest.client.AbstractClient;
import com.knime.enterprise.server.rest.providers.json.GenericJSONDeserializer;
import com.knime.enterprise.server.rest.providers.json.GenericJSONSerializer;
import com.knime.enterprise.server.rest.providers.json.JsonValueJSONSerializer;
import com.knime.enterprise.server.rest.providers.json.MapJSONDeserializer;
import com.knime.enterprise.server.rest.providers.json.MasonDeserializer;
import com.knime.enterprise.server.util.ClientTypeHeader;
import com.knime.enterprise.utility.KnimeServerConstants;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.ext.RuntimeDelegate;
import jakarta.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

/**
 * Abstract REST service client.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
abstract class AbstractHubServiceClient implements Closeable {

    private static final String CLIENT_TYPE = "Explorer01";

    private static final byte[] CLIENT_KEY = Base64.getDecoder().decode("nXNc6+rPTlgg6ZvjvlpfvQ==");

    static final HeaderDelegate<EntityTag> ETAG_DELEGATE =
        RuntimeDelegate.getInstance().createHeaderDelegate(EntityTag.class);

    Client m_client;

    final String m_hubBaseUrl;

    Authenticator m_authenticator;

    final NodeLogger m_logger;

    /**
     * @param clientBuilder client builder, may be created via {@link #createClientBuilder(int, int)}
     * @param hubBaseUrl Hub API base URL (excluding {@code /rest/v4})
     * @param authenticator Hub authenticator
     */
    AbstractHubServiceClient(final ClientBuilder clientBuilder, final String hubBaseUrl,
            final Authenticator authenticator) {
        m_client = clientBuilder.build();
        m_hubBaseUrl = hubBaseUrl;
        m_authenticator = authenticator;
        m_logger = NodeLogger.getLogger(getClass());
    }

    /**
     * @return the Hub API's base URL (e.g. {@code "https://api.hub.knime.com"})
     */
    public URI getHubAPIBaseURL() {
        return URI.create(m_hubBaseUrl);
    }

    /**
     * Creates a builder for a default web client configured to access Hub endpoints.
     *
     * @param connectTimeout connection connect timeout
     * @param readTimeout connection read timeout
     * @return client builder
     */
    static ClientBuilder createClientBuilder(final Duration connectTimeout, final Duration readTimeout) {
        return ClientBuilder.newBuilder() //
            .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS) //
            .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS) //
            .hostnameVerifier(KNIMEServerHostnameVerifier.getInstance());
    }

    /**
     * Creates a request which contains a valid Hub authorization header.
     *
     * @param target web target description
     * @return authorized request against the given target
     * @throws ResourceAccessException if the request could not be authorized
     */
    Invocation.Builder authorizedRequest(final WebTarget target, final boolean custom) throws ResourceAccessException {
        target.register(new GenericJSONSerializer(true));
        target.register(new JsonValueJSONSerializer());
        target.register(new MapJSONDeserializer());
        target.register(custom ? new GenericJSONDeserializer() : new MasonDeserializer());
        final var request = target.request();
        if (m_authenticator != null
                && !(m_authenticator instanceof RestAuthenticator restAuth && !restAuth.isAuthenticated())) {
            try {
                request.header(HttpHeaders.AUTHORIZATION, m_authenticator.getAuthorization());
            } catch (final CouldNotAuthorizeException e) {
                throw new ResourceAccessException("Could not authorize Hub REST call: " + e.getMessage(), e);
            }
        }

        // set the client version and UI type
        request.header(KnimeServerConstants.REST_API_VERSION_HEADER, AbstractClient.CLIENT_VERSION);
        request.header(KnimeServerConstants.KNIME_UI, EclipseUtil.currentUIPerspective().orElse("none"));

        final var path = target.getUri().getPath();
        final var idx = path.indexOf("/rest/") < 0 && path.endsWith("/rest") ? path.indexOf("/rest")
            : path.indexOf("/rest/");
        request.header(ClientTypeHeader.CLIENT_TYPE_HEADER,
            ClientTypeHeader.getClientTypeHeader(CLIENT_TYPE, path.substring(Math.max(idx, 0)), CLIENT_KEY));

        return request;
    }

    /**
     * Checks that the given response signals success (via a 4XX HTTP status code).
     *
     * @param response response to check
     * @throws ResourceAccessException if the request was unsuccessful
     */
    static void checkSuccessful(final Response response) throws ResourceAccessException {
        final var statusInfo = response.getStatusInfo();
        final var statusFamily = statusInfo.getFamily();
        if (statusFamily != Family.SUCCESSFUL) {
            final String message;
            if (statusFamily == Family.REDIRECTION
                    && response instanceof org.apache.cxf.jaxrs.impl.ResponseImpl cxfResponse) {
                final var location = cxfResponse.getOutMessage().get("transport.retransmit.url");
                message = "Redirect failed (firewall?): '%s'".formatted(location);
            } else {
                message = StringUtils.getIfBlank(response.hasEntity() ? response.readEntity(String.class) : null,
                    statusInfo::getReasonPhrase);
            }
            throw HttpExceptionUtils.wrapException(statusInfo.getStatusCode(), message);
        }
    }

    @Override
    public final void close() {
        if (m_client != null) {
            try {
                m_client.close();
            } finally {
                m_client = null;
            }
        }
    }
}
