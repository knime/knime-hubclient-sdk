package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Icon metadata for search results.
 *
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    private Icon(@JsonProperty(JSON_PROPERTY_DATA) final String data,
        @JsonProperty(JSON_PROPERTY_TYPE) final String type,
        @JsonProperty(JSON_PROPERTY_DEPRECATED) final boolean deprecated,
        @JsonProperty(JSON_PROPERTY_STREAMABLE) final boolean streamable,
        @JsonProperty(JSON_PROPERTY_IN_PORTS) final List<Port> inPorts,
        @JsonProperty(JSON_PROPERTY_OUT_PORTS) final List<Port> outPorts,
        @JsonProperty(JSON_PROPERTY_HAS_DYN_IN_PORTS) final boolean hasDynInPorts,
        @JsonProperty(JSON_PROPERTY_HAS_DYN_OUT_PORTS) final boolean hasDynOutPorts) {
        m_data = data;
        m_type = type;
        m_deprecated = deprecated;
        m_streamable = streamable;
        m_inPorts = inPorts == null ? new ArrayList<>() : inPorts;
        m_outPorts = outPorts == null ? new ArrayList<>() : outPorts;
        m_hasDynInPorts = hasDynInPorts;
        m_hasDynOutPorts = hasDynOutPorts;
    }

    @JsonProperty(JSON_PROPERTY_DATA)
    public String getData() {
        return m_data;
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
    public boolean hasDynamicInPorts() {
        return m_hasDynInPorts;
    }

    @JsonProperty(JSON_PROPERTY_HAS_DYN_OUT_PORTS)
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
