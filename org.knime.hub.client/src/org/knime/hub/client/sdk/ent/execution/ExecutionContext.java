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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing an KNIME Hub execution context.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.2
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ExecutionContext {

    private static final String JSON_PROPERTY_ID = "id";
    private final String m_id;

    private static final String JSON_PROPERTY_NAME = "name";
    private final String m_name;

    private static final String JSON_PROPERTY_DEFAULT_FOR_SPACES = "defaultForSpaces";
    private final List<String> m_defaultForSpaces;

    @JsonCreator
    private ExecutionContext(@JsonProperty(value = JSON_PROPERTY_ID, required = true) final String id,
        @JsonProperty(value = JSON_PROPERTY_NAME, required = true) final String name,
        @JsonProperty(value = JSON_PROPERTY_DEFAULT_FOR_SPACES) final List<String> defaultForSpaces) {
        m_id = id;
        m_name = name;
        m_defaultForSpaces = defaultForSpaces;
    }

    /**
     * Retrieves the execution context ID.
     *
     * @return id
     */
    @JsonProperty(JSON_PROPERTY_ID)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getId() {
        return m_id;
    }

    /**
     * Retrieves the execution context name.
     *
     * @return name
     */
    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getName() {
        return m_name;
    }

    /**
     * Retrieves a list of space IDs for which the execution context is a default..
     *
     * @return defaultForSpaces
     */
    @JsonProperty(JSON_PROPERTY_DEFAULT_FOR_SPACES)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    public List<String> getDefaultForSpaces() {
        return Optional.ofNullable(m_defaultForSpaces).orElse(List.of());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var executionContext = (ExecutionContext)o;
        return Objects.equals(this.m_id, executionContext.m_id) && Objects.equals(this.m_name, executionContext.m_name)
            && Objects.equals(this.m_defaultForSpaces, executionContext.m_defaultForSpaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_id, m_name, m_defaultForSpaces);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }

}
