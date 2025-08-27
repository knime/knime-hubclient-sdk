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
 * POJO representing a global hub group.
 *
 * @author Magnus, Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Group {

    private static final String JSON_PROPERTY_NAME = "name";
    private final String m_name;

    private static final String JSON_PROPERTY_DISPLAY_NAME = "displayName";
    private final String m_displayName;

    private static final String JSON_PROPERTY_ID = "id";
    private final String m_id;

    private static final String JSON_PROPERTY_EXTERNAL = "external";
    private final boolean m_external;

    private static final String JSON_PROPERTY_SCIM_MANAGED = "scimManaged";
    private final boolean m_scimManaged;

    private static final String JSON_PROPERTY_MEMBERS = "members";
    private final List<Member> m_members;

    private static final String JSON_PROPERTY_CONTROLS = "@controls";
    private final Map<String, Control> m_controls;

    @JsonCreator
    private Group(
            @JsonProperty(value = JSON_PROPERTY_NAME, required = true) final String name,
            @JsonProperty(value = JSON_PROPERTY_DISPLAY_NAME) final String displayName,
            @JsonProperty(value = JSON_PROPERTY_ID) final String id,
            @JsonProperty(value = JSON_PROPERTY_EXTERNAL) final boolean external,
            @JsonProperty(value = JSON_PROPERTY_SCIM_MANAGED) final boolean scimManaged,
            @JsonProperty(value = JSON_PROPERTY_MEMBERS) final List<Member> members,
            @JsonProperty(value = JSON_PROPERTY_CONTROLS) final Map<String, Control> controls) {
        m_name = name;
        m_displayName = displayName;
        m_id = id;
        m_external = external;
        m_scimManaged = scimManaged;
        m_members = members;
        m_controls = controls;
    }

    /**
     * Retrieves the group name.
     *
     * @return name
     */
    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getName() {
        return m_name;
    }

    /**
     * Retrieves the group display name.
     *
     * @return display name
     */
    @JsonProperty(JSON_PROPERTY_DISPLAY_NAME)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDisplayName() {
        return m_displayName;
    }

    /**
     * Retrieves the group ID.
     *
     * @return id
     */
    @JsonProperty(JSON_PROPERTY_ID)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getId() {
        return m_id;
    }

    /**
     * Returns <code>true</code> if the group is external or a group managed by KNIME Hub.
     *
     * @return external
     */
    @JsonProperty(JSON_PROPERTY_EXTERNAL)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public boolean isExternal() {
        return m_external;
    }

    /**
     * Returns <code>true</code> if this external group is managed by SCIM.
     *
     * @return scimManaged
     */
    @JsonProperty(JSON_PROPERTY_SCIM_MANAGED)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public boolean isScimManaged() {
        return m_scimManaged;
    }

    /**
     * Retrieves the members of this group.
     *
     * @return members
     */
    @JsonProperty(JSON_PROPERTY_MEMBERS)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Member> getMembers() {
        return Optional.ofNullable(m_members).orElseGet(List::of);
    }

    /**
     * Retrieves the controls of this group.
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
        var group = (Group)o;
        return Objects.equals(this.m_external, group.m_external)
            && Objects.equals(this.m_scimManaged, group.m_scimManaged)
            && Objects.equals(m_name, group.m_name)
            && Objects.equals(m_displayName, group.m_displayName)
            && Objects.equals(m_id, group.m_id)
            && Objects.equals(m_members, group.m_members)
            && Objects.equals(m_controls, group.m_controls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_name, m_displayName, m_id, m_external, m_scimManaged, m_members, m_controls);
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

