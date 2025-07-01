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
 *   Jun 30, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.execution;

import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing the body of a deployment creation request.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DeploymentCreationBody {

    private static final String JSON_PROPERTY_NAME = "name";
    private final String m_name;

    private static final String JSON_PROPERTY_ITEM_VERSION = "itemVersion";
    private final String m_itemVersion;

    private static final String JSON_PROPERTY_EXECUTION_CONTEXT = "executionContext";
    private final String m_executionContext;

    @JsonCreator
    DeploymentCreationBody(
            @JsonProperty(value = JSON_PROPERTY_NAME) final String name,
            @JsonProperty(value = JSON_PROPERTY_ITEM_VERSION) final String itemVersion,
            @JsonProperty(value = JSON_PROPERTY_EXECUTION_CONTEXT) final String executionContext) {
        this.m_name = name;
        this.m_itemVersion = itemVersion;
        this.m_executionContext = executionContext;
    }

    /**
     * Retrieves the name of the deployment.
     *
     * @return name
     */
    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public String getName() {
        return m_name;
    }

    /**
     * Retrieves the item version of the workflow which is deployed.
     *
     * @return itemVersion
     */
    @JsonProperty(JSON_PROPERTY_ITEM_VERSION)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public String getItemVersion() {
        return m_itemVersion;
    }

    /**
     * Retrieves the execution context ID of the deployment.
     *
     * @return executionContext
     */
    @JsonProperty(JSON_PROPERTY_EXECUTION_CONTEXT)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public String getExecutionContext() {
        return m_executionContext;
    }

    /**
     * Creates a new builder for {@link DeploymentCreationBody}.
     *
     * @return {@link DeploymentCreationBodyBuilder}
     */
    public static DeploymentCreationBodyBuilder builder() {
        return new DeploymentCreationBodyBuilder();
    }

    /**
     * Builder for {@link DeploymentCreationBody}.
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    public static final class DeploymentCreationBodyBuilder {
        private String m_name;
        private String m_itemVersion;
        private String m_executionContext;

        private DeploymentCreationBodyBuilder() {
        }

        /**
         * Sets the name of deployment.
         *
         * @param name the name of the deployment
         * @return this
         */
        public DeploymentCreationBodyBuilder withName(final String name) {
            this.m_name = name;
            return this;
        }

        /**
         * Sets the item version of the workflow to be deployed.
         *
         * @param itemVersion the item version of the workflow which is deployed
         * @return this
         */
        public DeploymentCreationBodyBuilder withItemVersion(final String itemVersion) {
            this.m_itemVersion = itemVersion;
            return this;
        }

        /**
         * Sets the execution context ID of the deployment.
         *
         * @param executionContext the execution context ID of the deployment
         * @return this
         */
        public DeploymentCreationBodyBuilder withExecutionContext(final String executionContext) {
            this.m_executionContext = executionContext;
            return this;
        }

        /**
         * Builds the {@link DeploymentCreationBody} instance.
         *
         * @return {@link DeploymentCreationBody}
         */
        public DeploymentCreationBody build() {
            return new DeploymentCreationBody(m_name, m_itemVersion, m_executionContext);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (DeploymentCreationBody) o;
        return Objects.equals(this.m_name, that.m_name)
                && Objects.equals(this.m_itemVersion, that.m_itemVersion)
                && Objects.equals(this.m_executionContext, that.m_executionContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_name, m_itemVersion, m_executionContext);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
