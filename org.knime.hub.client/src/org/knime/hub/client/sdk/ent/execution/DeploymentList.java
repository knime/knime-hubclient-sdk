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
 *   Jun 27, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.execution;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.Control;
import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing a list of deployments
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.2
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DeploymentList {

    private static final String JSON_PROPERTY_CONTROLS = "@controls";
    private final Map<String, Control> m_masonControls;

    private static final String JSON_PROPERTY_PAGINATION_INFO = "pagination";
    private final PaginationInfo m_pagination;

    private static final String JSON_PROPERTY_DEPLOYMENTS = "deployments";
    private final List<Deployment> m_deployments;

    @JsonCreator
    private DeploymentList(@JsonProperty(value = JSON_PROPERTY_CONTROLS) final Map<String, Control> masonControls,
        @JsonProperty(value = JSON_PROPERTY_PAGINATION_INFO) final PaginationInfo pagination,
        @JsonProperty(value = JSON_PROPERTY_DEPLOYMENTS) final List<Deployment> deployments) {
        m_masonControls = masonControls;
        m_pagination = pagination;
        m_deployments = deployments;
    }

    /**
     * Retrieves the mason controls.
     *
     * @return masonControls
     */
    @JsonProperty(JSON_PROPERTY_CONTROLS)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    public Map<String, Control> getControls() {
        return Optional.ofNullable(m_masonControls).orElseGet(Collections::emptyMap);
    }

    /**
     * Retrieves the pagination info
     *
     * @return pagination
     */
    @JsonProperty(JSON_PROPERTY_PAGINATION_INFO)
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT)
    public Optional<PaginationInfo> getPagination() {
        return Optional.ofNullable(m_pagination);
    }

    /**
     * Retrieves the list of deployments.
     *
     * @return deployments
     */
    @JsonProperty(JSON_PROPERTY_DEPLOYMENTS)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    public List<Deployment> getDeployments() {
        return Optional.ofNullable(m_deployments).orElse(List.of());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var deploymentList = (DeploymentList)o;
        return Objects.equals(this.m_masonControls, deploymentList.m_masonControls)
                && Objects.equals(this.m_pagination, deploymentList.m_pagination)
                && Objects.equals(this.m_deployments, deploymentList.m_deployments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_masonControls, m_pagination, m_deployments);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }

}
