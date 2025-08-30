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
 *   Jun 23, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.catalog;

import java.util.List;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing a list of named item versions of a {@link RepositoryItem}.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.2
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NamedItemVersionList {

    private static final String JSON_PROPERTY_TOTAL_COUNT = "totalCount";
    private int m_totalCount;

    private static final String JSON_PROPERTY_VERSIONS = "versions";
    private List<NamedItemVersion> m_versions;

    @JsonCreator
    private NamedItemVersionList(@JsonProperty(value = JSON_PROPERTY_TOTAL_COUNT, required = true) final int totalCount,
        @JsonProperty(value = JSON_PROPERTY_VERSIONS, required = true) final List<NamedItemVersion> versions) {
        m_totalCount = totalCount;
        m_versions = versions;
    }

    /**
     * Retrieves the total version count.
     *
     * @return totalCount
     */
    @JsonProperty(JSON_PROPERTY_TOTAL_COUNT)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public int getTotalCount() {
        return m_totalCount;
    }

    /**
     * Retrieves the named item versions.
     *
     * @return versions
     */
    @JsonProperty(JSON_PROPERTY_VERSIONS)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public List<NamedItemVersion> getVersions() {
        return m_versions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var namedItemVersionList = (NamedItemVersionList) o;
        return Objects.equals(this.m_totalCount, namedItemVersionList.m_totalCount)
                && Objects.equals(this.m_versions, namedItemVersionList.m_versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_totalCount, m_versions);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }

}
