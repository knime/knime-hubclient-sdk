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
 *   Jul 9, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.account;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.Control;
import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing global hub installation account information.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HubInstallationAccount {

    private static final String JSON_PROPERTY_ID = "id";
    private final String m_id;

    private static final String JSON_PROPERTY_NAME = "name";
    private final String m_name;

    private static final String JSON_PROPERTY_TYPE = "type";
    private final String m_type;

    private static final String JSON_PROPERTY_VERSION = "version";
    private final String m_version;

    private static final String JSON_PROPERTY_CLUSTER_ID = "clusterId";
    private final String m_clusterId;

    private static final String JSON_PROPERTY_FEATURES = "features";
    private final List<String> m_features;

    private static final String JSON_PROPERTY_GROUPS = "groups";
    private final List<Group> m_groups;

    private static final String JSON_PROPERTY_CONTROLS = "@controls";
    private final Map<String, Control> m_controls;

    @JsonCreator
    private HubInstallationAccount(
            @JsonProperty(value = JSON_PROPERTY_ID, required = true) final String id,
            @JsonProperty(value = JSON_PROPERTY_NAME, required = true) final String name,
            @JsonProperty(value = JSON_PROPERTY_TYPE, required = true) final String type,
            @JsonProperty(value = JSON_PROPERTY_VERSION) final String version,
            @JsonProperty(value = JSON_PROPERTY_CLUSTER_ID) final String clusterId,
            @JsonProperty(value = JSON_PROPERTY_FEATURES, required = true) final List<String> features,
            @JsonProperty(value = JSON_PROPERTY_GROUPS) final List<Group> groups,
            @JsonProperty(value = JSON_PROPERTY_CONTROLS) final Map<String, Control> controls) {
        m_id = id;
        m_name = name;
        m_type = type;
        m_version = version;
        m_clusterId = clusterId;
        m_features = features;
        m_groups = groups;
        m_controls = controls;
    }

    /**
     * Retrieves the ID of the hub installation account.
     *
     * @return id
     */
    @JsonProperty(JSON_PROPERTY_ID)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getId() {
        return m_id;
    }

    /**
     * Retrieves the name of the hub installation account.
     *
     * @return name
     */
    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getName() {
        return m_name;
    }

    /**
     * Retrieves the type of the hub installation account.
     *
     * @return type
     */
    @JsonProperty(JSON_PROPERTY_TYPE)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getType() {
        return m_type;
    }

    /**
     * Retrieves the optional business hub version.
     *
     * @return version
     */
    @JsonProperty(JSON_PROPERTY_VERSION)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getVersion() {
        return m_version;
    }

    /**
     * Retrieves ID of the kubernetes cluster.
     *
     * @return clusterId
     */
    @JsonProperty(JSON_PROPERTY_CLUSTER_ID)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getClusterId() {
        return m_clusterId;
    }

    /**
     * Retrieves the available features of this hub installation.
     *
     * @return features
     */
    @JsonProperty(JSON_PROPERTY_FEATURES)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public List<String> getFeatures() {
        return m_features;
    }

    /**
     * Retrieves the list of global groups.
     *
     * @return groups
     */
    @JsonProperty(JSON_PROPERTY_GROUPS)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Group> getGroups() {
        return Optional.ofNullable(m_groups).orElseGet(List::of);
    }

    /**
     * Retrieves the controls of this hub account installation.
     *
     * @return controls
     */
    @JsonProperty(JSON_PROPERTY_CONTROLS)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Control> getControls() {
        return Optional.ofNullable(m_controls).orElseGet(Collections::emptyMap);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var hubInstallationAccount = (HubInstallationAccount)o;
        return Objects.equals(m_id, hubInstallationAccount.m_id)
            && Objects.equals(m_name, hubInstallationAccount.m_name)
            && Objects.equals(m_type, hubInstallationAccount.m_type)
            && Objects.equals(m_version, hubInstallationAccount.m_version)
            && Objects.equals(m_clusterId, hubInstallationAccount.m_clusterId)
            && Objects.equals(m_features, hubInstallationAccount.m_features)
            && Objects.equals(m_groups, hubInstallationAccount.m_groups)
            && Objects.equals(m_controls, hubInstallationAccount.m_controls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_id, m_name, m_type, m_version, m_clusterId, m_features, m_groups, m_controls);
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperUtil.getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON: ", e);
        }
    }

}
