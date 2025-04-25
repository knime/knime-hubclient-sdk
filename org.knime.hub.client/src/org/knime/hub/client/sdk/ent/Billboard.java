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
package org.knime.hub.client.sdk.ent;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.knime.core.util.Version;
import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing the Billboard which contains information about the hub instance.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Billboard {

    private static final String JSON_PROPERTY_MOUNT_ID = "mountId";
    private final String m_mountId;

    private static final String JSON_PROPERTY_ENABLE_RESET_ON_UPLOAD_CHECKBOX = "enableResetOnUploadCheckbox";
    private final Boolean m_enableResetOnUploadCheckbox;

    private static final String JSON_PROPERTY_OAUTH_INFORMATION = "oAuthConfig";
    private final OAuthAuthenticationInformation m_oAuthInformation;

    private static final String JSON_PROPERTY_FORCE_RESET_ON_UPLOAD = "forceResetOnUpload";
    private final Boolean m_forceResetOnUpload;

    private static final String JSON_PROPERTY_FETCH_TIMEOUT = "fetchTimeout";
    private final Duration m_fetchTimeout;

    private static final String JSON_PROPERTY_FETCH_INTERVAL = "fetchInterval";
    private final Duration m_fetchInterval;

    private static final String JSON_PROPERTY_MASON_CONTROLS = "@controls";
    private final Map<String, Control> m_controls;

    private static final String JSON_PROPERTY_PREFERRED_AUTH_TYPE = "preferredAuthType";
    private final AuthenticationType m_preferredAuthType;

    private static final String JSON_PROPERTY_VERSION = "version";
    private final Version m_version;

    @JsonCreator
    private Billboard(
        @JsonProperty(value = JSON_PROPERTY_MOUNT_ID, required = false) final String mountId,
        @JsonProperty(value = JSON_PROPERTY_ENABLE_RESET_ON_UPLOAD_CHECKBOX, required = false)
        final Boolean enableResetOnUploadCheckbox,
        @JsonProperty(value = JSON_PROPERTY_OAUTH_INFORMATION, required = false)
        final OAuthAuthenticationInformation oAuthInfo,
        @JsonProperty(value = JSON_PROPERTY_FORCE_RESET_ON_UPLOAD, required = false) final Boolean forceResetOnUpload,
        @JsonProperty(value = JSON_PROPERTY_FETCH_TIMEOUT, required = false) final Duration fetchTimeout,
        @JsonProperty(value = JSON_PROPERTY_FETCH_INTERVAL, required = false) final Duration fetchInterval,
        @JsonProperty(value = JSON_PROPERTY_MASON_CONTROLS, required = false) final Map<String, Control> controls,
        @JsonProperty(value = JSON_PROPERTY_PREFERRED_AUTH_TYPE, required = false)
        final AuthenticationType preferredAuthType,
        @JsonProperty(value = JSON_PROPERTY_VERSION, required = false) final Version version) {
        m_version = version;
        m_mountId = mountId;
        m_preferredAuthType = preferredAuthType;
        m_oAuthInformation = oAuthInfo;
        m_forceResetOnUpload = forceResetOnUpload;
        m_enableResetOnUploadCheckbox = enableResetOnUploadCheckbox;
        m_fetchInterval = fetchInterval;
        m_fetchTimeout = fetchTimeout;
        m_controls = controls;
    }

    /**
     * Returns the default mount id of the server.
     *
     * @return The default mount id.
     */
    @JsonProperty(JSON_PROPERTY_MOUNT_ID)
    public String getMountId() {
        return m_mountId;
    }

    /**
     * Returns whether the reset on upload checkbox in the deploy dialog should be enabled if
     * {@link Billboard#hasForceResetOnUpload()} equals {@code true}.
     *
     * @return <code>true</code> if the checkbox should be enabled, <code>false</code> otherwise.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty(JSON_PROPERTY_ENABLE_RESET_ON_UPLOAD_CHECKBOX)
    public Optional<Boolean> isEnableResetOnUploadCheckbox() {
        return Optional.ofNullable(m_enableResetOnUploadCheckbox);
    }

    /**
     * The OAuth information needed for authentication.
     *
     * @return the necessary information.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty(JSON_PROPERTY_OAUTH_INFORMATION)
    public Optional<OAuthAuthenticationInformation> getOAuthInformation() {
        return Optional.ofNullable(m_oAuthInformation);
    }

    /**
     * Returns whether the workflow should be reset before being uploaded.
     *
     * @return <code>true</code> if the workflow should be reset, <code>false</code> otherwise.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty(JSON_PROPERTY_FORCE_RESET_ON_UPLOAD)
    public Optional<Boolean> hasForceResetOnUpload() {
        return Optional.ofNullable(m_forceResetOnUpload);
    }

    /**
     * Returns the repository fetching read timeout for client in ms.
     *
     * @return the timeout
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty(JSON_PROPERTY_FETCH_TIMEOUT)
    public Optional<Duration> getClientExplorerFetchTimeout() {
        return Optional.ofNullable(m_fetchTimeout);
    }

    /**
     * Returns the fetch interval of the repository for clients in ms.
     *
     * @return the refresh interval
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty(JSON_PROPERTY_FETCH_INTERVAL)
    public Optional<Duration> getClientExplorerFetchInterval() {
        return Optional.ofNullable(m_fetchInterval);
    }

    /**
     * Retrieves the (possibly empty) map with all controls for this item.
     *
     * @return controls
     */
    @JsonProperty(JSON_PROPERTY_MASON_CONTROLS)
    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Control> getMasonControls() {
        return Optional.ofNullable(m_controls).orElseGet(Collections::emptyMap);
    }

    /**
     * Returns the preferred authentication type.
     *
     * @return The preferred authentication type.
     */
    @JsonProperty(JSON_PROPERTY_PREFERRED_AUTH_TYPE)
    public AuthenticationType getPreferredAuthType() {
        return m_preferredAuthType;
    }

    /**
     * Returns the version of the server.
     *
     * @return The server version.
     */
    @JsonProperty(JSON_PROPERTY_VERSION)
    public Version getVersion() {
        return m_version;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var billboard = (Billboard) o;
        return Objects.equals(this.m_mountId, billboard.m_mountId)
                && Objects.equals(this.m_enableResetOnUploadCheckbox, billboard.m_enableResetOnUploadCheckbox)
                && Objects.equals(this.m_oAuthInformation, billboard.m_oAuthInformation)
                && Objects.equals(this.m_forceResetOnUpload, billboard.m_forceResetOnUpload)
                && Objects.equals(this.m_fetchTimeout, billboard.m_fetchTimeout)
                && Objects.equals(this.m_fetchInterval, billboard.m_fetchInterval)
                && Objects.equals(this.m_controls, billboard.m_controls)
                && Objects.equals(this.m_preferredAuthType, billboard.m_preferredAuthType)
                && Objects.equals(this.m_version, billboard.m_version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_mountId, m_enableResetOnUploadCheckbox, m_oAuthInformation, m_forceResetOnUpload,
            m_fetchTimeout, m_fetchInterval, m_controls, m_preferredAuthType, m_version);
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperUtil.getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON: ", e);
        }
    }

    /**
     * Enum of authentication types of the hub
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    public enum AuthenticationType {
            /** OAuth authorization */
            OAuth,
            /** Credentials authorization */
            Credentials;
    }

}
