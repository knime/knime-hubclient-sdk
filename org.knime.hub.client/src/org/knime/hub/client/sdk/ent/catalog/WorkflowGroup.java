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

package org.knime.hub.client.sdk.ent.catalog;

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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing a workflow group.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo( //
        use = JsonTypeInfo.Id.NAME, //
        include = JsonTypeInfo.As.PROPERTY, //
        property = RepositoryItem.JSON_PROPERTY_TYPE, //
        visible = true, //
        requireTypeIdForSubtypes = OptBoolean.TRUE
)
@JsonSubTypes({ @JsonSubTypes.Type(value = Space.class, name = Space.TYPE) })
public sealed class WorkflowGroup extends RepositoryItem permits Space {

    /** Type of a workflow group and space */
    protected static final String TYPE = "WorkflowGroup";

    /** JSON key of the workflow groups children property */
    protected static final String JSON_PROPERTY_CHILDREN = "children";
    private final List<RepositoryItem> m_children;

    /**
     * Workflow group
     *
     * @param path the path of a workflow group
     * @param canonicalPath the canonical path of the workflow group
     * @param id the ID of a workflow group
     * @param owner the owner of a workflow group
     * @param description the description of a workflow group
     * @param details the details of a workflow group
     * @param masonControls the mason controls of a workflow group
     * @param children the children of a workflow group
     */
    @JsonCreator
    protected WorkflowGroup(@JsonProperty(value = RepositoryItem.JSON_PROPERTY_PATH, required = true) final String path,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_CANONICAL_PATH, required = true) final String canonicalPath,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_ID, required = true) final String id,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_OWNER, required = true) final String owner,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_DESCRIPTION) final String description,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_DETAILS) final MetaInfo details,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_MASON_CONTROLS) final Map<String, Control> masonControls,
        @JsonProperty(value = WorkflowGroup.JSON_PROPERTY_CHILDREN) final List<RepositoryItem> children) {
        super(path, canonicalPath, id, owner, description, details, masonControls);
        this.m_children = children;
    }

    /**
     * Retrieves the workflow groups children.
     *
     * @return children
     */
    @JsonProperty(JSON_PROPERTY_CHILDREN)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    public List<RepositoryItem> getChildren() {
        return Optional.ofNullable(m_children).orElseGet(Collections::emptyList);
    }

    @Override
    public RepositoryItemType getType() {
        return RepositoryItemType.WORKFLOW_GROUP;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var workflowGroup = (WorkflowGroup) o;
        return Objects.equals(this.m_children, workflowGroup.m_children) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_children, super.hashCode());
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
