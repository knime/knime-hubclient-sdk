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
 *  Nodes are deemed to be separate and independent programs and are not
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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.knime.hub.client.sdk.ent.util.EntityUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Icon metadata for search results.
 *
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings({"java:S1176", "MissingJavadoc"})
public final class Icon {

    private static final String JSON_PROPERTY_DATA = "data";
    private final String m_data;

    private static final String JSON_PROPERTY_TYPE = "type";
    private final String m_type;

    private static final String JSON_PROPERTY_DEPRECATED = "deprecated";
    private final boolean m_deprecated;

    private static final String JSON_PROPERTY_STREAMABLE = "streamable";
    private final boolean m_streamable;

    private static final String JSON_PROPERTY_IN_PORTS = "inPorts";
    private final List<Port> m_inPorts;

    private static final String JSON_PROPERTY_OUT_PORTS = "outPorts";
    private final List<Port> m_outPorts;

    private static final String JSON_PROPERTY_HAS_DYN_IN_PORTS = "hasDynInPorts";
    private final boolean m_hasDynInPorts;

    private static final String JSON_PROPERTY_HAS_DYN_OUT_PORTS = "hasDynOutPorts";
    private final boolean m_hasDynOutPorts;

    @JsonCreator
    public Icon(@JsonProperty(JSON_PROPERTY_DATA) final String data,
        @JsonProperty(value = JSON_PROPERTY_TYPE) final String type,
        @JsonProperty(value = JSON_PROPERTY_DEPRECATED, required = true) final boolean deprecated,
        @JsonProperty(value = JSON_PROPERTY_STREAMABLE, required = true) final boolean streamable,
        @JsonProperty(value = JSON_PROPERTY_IN_PORTS, required = true) final List<Port> inPorts,
        @JsonProperty(value = JSON_PROPERTY_OUT_PORTS, required = true) final List<Port> outPorts,
        @JsonProperty(value = JSON_PROPERTY_HAS_DYN_IN_PORTS, required = true) final boolean hasDynInPorts,
        @JsonProperty(value = JSON_PROPERTY_HAS_DYN_OUT_PORTS, required = true) final boolean hasDynOutPorts) {
        m_data = data;
        m_type = type;
        m_deprecated = deprecated;
        m_streamable = streamable;
        m_inPorts = inPorts;
        m_outPorts = outPorts;
        m_hasDynInPorts = hasDynInPorts;
        m_hasDynOutPorts = hasDynOutPorts;
    }

    @JsonProperty(JSON_PROPERTY_DATA)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getData() {
        return Optional.ofNullable(m_data);
    }

    @JsonProperty(JSON_PROPERTY_TYPE)
    public String getType() {
        return m_type;
    }

    @JsonProperty(JSON_PROPERTY_DEPRECATED)
    public boolean isDeprecated() {
        return m_deprecated;
    }

    @JsonProperty(JSON_PROPERTY_STREAMABLE)
    public boolean isStreamable() {
        return m_streamable;
    }

    @JsonProperty(JSON_PROPERTY_IN_PORTS)
    public List<Port> getInPorts() {
        return m_inPorts;
    }

    @JsonProperty(JSON_PROPERTY_OUT_PORTS)
    public List<Port> getOutPorts() {
        return m_outPorts;
    }

    @JsonProperty(JSON_PROPERTY_HAS_DYN_IN_PORTS)
    @SuppressWarnings("java:S1176") // S1176: accessor naming is self-descriptive
    public boolean hasDynamicInPorts() {
        return m_hasDynInPorts;
    }

    @JsonProperty(JSON_PROPERTY_HAS_DYN_OUT_PORTS)
    @SuppressWarnings("java:S1176") // S1176: accessor naming is self-descriptive
    public boolean hasDynamicOutPorts() {
        return m_hasDynOutPorts;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (Icon)o;
        return m_deprecated == that.m_deprecated
            && m_streamable == that.m_streamable
            && m_hasDynInPorts == that.m_hasDynInPorts
            && m_hasDynOutPorts == that.m_hasDynOutPorts
            && Objects.equals(m_data, that.m_data)
            && Objects.equals(m_type, that.m_type)
            && Objects.equals(m_inPorts, that.m_inPorts)
            && Objects.equals(m_outPorts, that.m_outPorts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_data, m_type, m_deprecated, m_streamable, m_inPorts, m_outPorts, m_hasDynInPorts,
            m_hasDynOutPorts);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
