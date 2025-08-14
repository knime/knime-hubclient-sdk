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
 *   Jul 1, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.catalog;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.Control;
import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing a metanode or subnode template in the server repository.
 *
 * Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.2
 */
public final class Template extends RepositoryItem implements Sized {

    static final String TYPE = "WorkflowTemplate";

    private static final String JSON_PROPERTY_SIZE = "size";
    private final long m_size;

    @JsonCreator
    private Template(@JsonProperty(value = RepositoryItem.JSON_PROPERTY_PATH, required = true) final String path,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_CANONICAL_PATH) final String canonicalPath,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_ID) final String id,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_OWNER, required = true) final String owner,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_OWNER_ACCOUNT_ID) final String ownerAccountId,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_CREATED_ON) final ZonedDateTime createdOn,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_DESCRIPTION) final String description,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_LAST_UPLOADED_ON) final ZonedDateTime lastUploadedOn,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_DETAILS) final MetaInfo details,
        @JsonProperty(value = RepositoryItem.JSON_PROPERTY_MASON_CONTROLS) final Map<String, Control> masonControls,
        @JsonProperty(value = Template.JSON_PROPERTY_SIZE) final long size) {
        super(path, canonicalPath, id, owner, ownerAccountId, createdOn, description, lastUploadedOn, details,
            masonControls);
        m_size = size;
    }

    /**
     * Retrieves the compressed template file size in bytes.
     *
     * @return size
     */
    @JsonProperty(JSON_PROPERTY_SIZE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    @Override
    public Long getSize() {
        return m_size;
    }

    @Override
    public RepositoryItemType getType() {
        return RepositoryItemType.WORKFLOW_TEMPLATE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var template = (Template) o;
        return Objects.equals(this.m_size, template.m_size) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_size, super.hashCode());
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }

}
