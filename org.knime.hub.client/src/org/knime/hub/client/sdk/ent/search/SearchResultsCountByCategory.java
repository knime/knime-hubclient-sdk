/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 */
package org.knime.hub.client.sdk.ent.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.knime.hub.client.sdk.ent.util.EntityUtil;

import java.util.Objects;

/**
 * Counts of search results per category.
 *
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.1
 */
@SuppressWarnings({"java:S1176", "MissingJavadoc"})
public final class SearchResultsCountByCategory {

    private static final String JSON_PROPERTY_ALL = "all";
    private final long m_all;

    private static final String JSON_PROPERTY_WORKFLOWS = "workflows";
    private final long m_workflows;

    private static final String JSON_PROPERTY_NODES = "nodes";
    private final long m_nodes;

    private static final String JSON_PROPERTY_EXTENSIONS = "extensions";
    private final long m_extensions;

    private static final String JSON_PROPERTY_COMPONENTS = "components";
    private final long m_components;

    private static final String JSON_PROPERTY_COLLECTIONS = "collections";
    private final long m_collections;

    @JsonCreator
    public SearchResultsCountByCategory(@JsonProperty(value = JSON_PROPERTY_ALL, required = true) final long all,
        @JsonProperty(value = JSON_PROPERTY_WORKFLOWS, required = true) final long workflows,
        @JsonProperty(value = JSON_PROPERTY_NODES, required = true) final long nodes,
        @JsonProperty(value = JSON_PROPERTY_EXTENSIONS, required = true) final long extensions,
        @JsonProperty(value = JSON_PROPERTY_COMPONENTS, required = true) final long components,
        @JsonProperty(value = JSON_PROPERTY_COLLECTIONS, required = true) final long collections) {
        m_all = all;
        m_workflows = workflows;
        m_nodes = nodes;
        m_extensions = extensions;
        m_components = components;
        m_collections = collections;
    }

    @JsonProperty(JSON_PROPERTY_ALL)
    public long getAll() {
        return m_all;
    }

    @JsonProperty(JSON_PROPERTY_WORKFLOWS)
    public long getWorkflows() {
        return m_workflows;
    }

    @JsonProperty(JSON_PROPERTY_NODES)
    public long getNodes() {
        return m_nodes;
    }

    @JsonProperty(JSON_PROPERTY_EXTENSIONS)
    public long getExtensions() {
        return m_extensions;
    }

    @JsonProperty(JSON_PROPERTY_COMPONENTS)
    public long getComponents() {
        return m_components;
    }

    @JsonProperty(JSON_PROPERTY_COLLECTIONS)
    public long getCollections() {
        return m_collections;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (SearchResultsCountByCategory)o;
        return m_all == that.m_all
            && m_workflows == that.m_workflows
            && m_nodes == that.m_nodes
            && m_extensions == that.m_extensions
            && m_components == that.m_components
            && m_collections == that.m_collections;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_all, m_workflows, m_nodes, m_extensions, m_components, m_collections);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
