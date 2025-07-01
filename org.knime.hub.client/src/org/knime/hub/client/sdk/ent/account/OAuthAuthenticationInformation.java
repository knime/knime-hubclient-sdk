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
 *   Apr 24, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.account;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing OAuth authentication information
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
public final class OAuthAuthenticationInformation {

    private static final String JSON_PROPERTY_TOKEN_ENDPOINT = "tokenEndpoint";
    private final URL m_tokenEndpoint;

    private static final String JSON_PROPERTY_CLIENT_ID = "clientId";
    private final String m_clientId;

    private static final String JSON_PROPERTY_AUTHORIZATION_ENDPOINT = "authorizationEndpoint";
    private final URL m_authorizationEndpoint;

    @JsonCreator
    private OAuthAuthenticationInformation(
        @JsonProperty(value = JSON_PROPERTY_TOKEN_ENDPOINT, required = false) final URL tokenEndpoint,
        @JsonProperty(value = JSON_PROPERTY_CLIENT_ID, required = false) final String clientId,
        @JsonProperty(value = JSON_PROPERTY_AUTHORIZATION_ENDPOINT, required = false) final URL authorizationEndpoint) {
        m_tokenEndpoint = tokenEndpoint;
        m_clientId = clientId;
        m_authorizationEndpoint = authorizationEndpoint;
    }

    /**
     * Returns the token end point to obtain an OAuth access token.
     *
     * @return the token end point.
     */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonProperty(JSON_PROPERTY_TOKEN_ENDPOINT)
    public Optional<URL> getTokenEndpoint() {
        return Optional.ofNullable(m_tokenEndpoint);
    }

    /**
     * Returns the client id that shall be used during OAuth authorization.
     *
     * @return the client id.
     */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonProperty(JSON_PROPERTY_CLIENT_ID)
    public Optional<String> getClientId() {
        return Optional.ofNullable(m_clientId);
    }

    /**
     * Returns the authorization end point to initialize OAuth authentication.
     *
     * @return the authorization end point.
     */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonProperty(JSON_PROPERTY_AUTHORIZATION_ENDPOINT)
    public Optional<URL> getAuthorizationEndpoint() {
        return Optional.ofNullable(m_authorizationEndpoint);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var oAuthInfo = (OAuthAuthenticationInformation) o;
        return Objects.equals(this.m_tokenEndpoint, oAuthInfo.m_tokenEndpoint)
                && Objects.equals(this.m_clientId, oAuthInfo.m_clientId)
                && Objects.equals(this.m_authorizationEndpoint, oAuthInfo.m_authorizationEndpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_tokenEndpoint, m_clientId, m_authorizationEndpoint);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }

}
