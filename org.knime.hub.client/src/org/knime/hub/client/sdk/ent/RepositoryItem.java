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

package org.knime.hub.client.sdk.ent;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing a repository item.
 * 
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(value = RepositoryItem.JSON_PROPERTY_TYPE, allowSetters = true, ignoreUnknown = true)
@JsonTypeInfo(//
        use = JsonTypeInfo.Id.NAME, //
        include = JsonTypeInfo.As.EXISTING_PROPERTY, // Also serialized via #getType().
        property = RepositoryItem.JSON_PROPERTY_TYPE, //
        visible = true, //
        requireTypeIdForSubtypes = OptBoolean.TRUE
)
@JsonSubTypes({ //
        @JsonSubTypes.Type(value = Component.class, name = Component.TYPE), //
        @JsonSubTypes.Type(value = Data.class, name = Data.TYPE), //
        @JsonSubTypes.Type(value = Workflow.class, name = Workflow.TYPE), //
        @JsonSubTypes.Type(value = WorkflowGroup.class, name = WorkflowGroup.TYPE), //
})
@JsonPropertyOrder({ RepositoryItem.JSON_PROPERTY_TYPE, RepositoryItem.JSON_PROPERTY_ID }) // Serialize first
public abstract sealed class RepositoryItem permits Component, Data, Workflow, WorkflowGroup {
    
    /**
     * Repository item type enum.
     */
    public enum RepositoryItemType {
        WORKFLOW_GROUP(WorkflowGroup.TYPE), //
        WORKFLOW(Workflow.TYPE), //
        COMPONENT(Component.TYPE), //
        DATA(Data.TYPE), //
        SPACE(Space.TYPE);

        private final String m_value;

        RepositoryItemType(String value) {
            this.m_value = value;
        }

        @JsonValue
        private final String getValue() {
            return m_value;
        }

        @Override
        public String toString() {
            return String.valueOf(m_value);
        }

        @JsonCreator
        private static RepositoryItemType fromValue(String value) {
            for (RepositoryItemType b : RepositoryItemType.values()) {
                if (b.m_value.equals(value)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }
    }

    protected static final String JSON_PROPERTY_TYPE = "type";
    // no member for type, instead use overridden #getType()

    protected static final String JSON_PROPERTY_PATH = "path";
    private final String m_path;

    protected static final String JSON_PROPERTY_ID = "id";
    private final String m_id;

    protected static final String JSON_PROPERTY_OWNER = "owner";
    private final String m_owner;

    protected static final String JSON_PROPERTY_DESCRIPTION = "description";
    private final String m_description;

    protected static final String JSON_PROPERTY_DETAILS = "details";
    private final MetaInfo m_details;

    protected static final String JSON_PROPERTY_MASON_CONTROLS = "@controls";
    private final Map<String, Control> m_masonControls;

    @JsonCreator
    protected RepositoryItem(@JsonProperty(value = RepositoryItem.JSON_PROPERTY_PATH, required = true) String path,
            @JsonProperty(value = RepositoryItem.JSON_PROPERTY_ID, required = true) String id,
            @JsonProperty(value = RepositoryItem.JSON_PROPERTY_OWNER, required = true) String owner,
            @JsonProperty(value = RepositoryItem.JSON_PROPERTY_DESCRIPTION) String description,
            @JsonProperty(value = RepositoryItem.JSON_PROPERTY_DETAILS) MetaInfo details,
            @JsonProperty(value = RepositoryItem.JSON_PROPERTY_MASON_CONTROLS) Map<String, Control> masonControls) {
        this.m_path = path;
        this.m_id = id;
        this.m_owner = owner;
        this.m_description = description;
        this.m_details = details;
        this.m_masonControls = masonControls;
    }

    /**
     * Retrieves the items absolute path in the repository.
     * 
     * @return path
     */
    @JsonProperty(JSON_PROPERTY_PATH)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getPath() {
        return m_path;
    }

    /**
     * Retrieves the items unique ID which does not change when the item is renamed
     * or moved.
     * 
     * @return id
     */
    @JsonProperty(JSON_PROPERTY_ID)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getId() {
        return m_id;
    }

    /**
     * Retrieves the items owner.
     * 
     * @return owner
     */
    @JsonProperty(JSON_PROPERTY_OWNER)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getOwner() {
        return m_owner;
    }

    /**
     * Retrieves the optional plain text description for this item
     * 
     * @return description
     */
    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Optional<String> getDescription() {
        return Optional.ofNullable(m_description);
    }

    /**
     * Retrieves the optional item details.
     * 
     * @return details
     */
    @JsonProperty(JSON_PROPERTY_DETAILS)
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT)
    public Optional<MetaInfo> getDetails() {
        return Optional.ofNullable(m_details);
    }

    /**
     * Retrieves the (possibly empty) map with all controls for this item.
     * 
     * @return masonControls
     */
    @JsonProperty(JSON_PROPERTY_MASON_CONTROLS)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    public Map<String, Control> getMasonControls() {
        return Optional.ofNullable(m_masonControls).orElseGet(Collections::emptyMap);
    }

    /**
     * Retrieves the item type.
     * 
     * @return type
     */
    @JsonProperty(JSON_PROPERTY_TYPE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public abstract RepositoryItemType getType();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var repositoryItem = (RepositoryItem) o;
        return Objects.equals(this.m_path, repositoryItem.m_path) && Objects.equals(this.m_id, repositoryItem.m_id)
                && Objects.equals(this.getType(), repositoryItem.getType())
                && Objects.equals(this.m_owner, repositoryItem.m_owner)
                && Objects.equals(this.m_description, repositoryItem.m_description)
                && Objects.equals(this.m_details, repositoryItem.m_details)
                && Objects.equals(this.m_masonControls, repositoryItem.m_masonControls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_path, m_id, getType(), m_owner, m_description, m_details, m_masonControls);
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperUtil.getInstance().getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON: ", e);
        }
    }
}
