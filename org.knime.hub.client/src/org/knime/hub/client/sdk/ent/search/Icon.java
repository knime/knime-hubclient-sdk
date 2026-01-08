package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;

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
    private static final String JSON_PROPERTY_TYPE = "type";
    private static final String JSON_PROPERTY_DEPRECATED = "deprecated";
    private static final String JSON_PROPERTY_STREAMABLE = "streamable";
    private static final String JSON_PROPERTY_IN_PORTS = "inPorts";
    private static final String JSON_PROPERTY_OUT_PORTS = "outPorts";
    private static final String JSON_PROPERTY_HAS_DYN_IN_PORTS = "hasDynInPorts";
    private static final String JSON_PROPERTY_HAS_DYN_OUT_PORTS = "hasDynOutPorts";

    private String m_data;
    private String m_type;
    private boolean m_deprecated;
    private boolean m_streamable;
    private List<Port> m_inPorts = new ArrayList<>();
    private List<Port> m_outPorts = new ArrayList<>();
    private boolean m_hasDynInPorts;
    private boolean m_hasDynOutPorts;

    @JsonProperty(JSON_PROPERTY_DATA)
    public String getData() {
        return m_data;
    }

    public void setData(final String data) {
        m_data = data;
    }

    @JsonProperty(JSON_PROPERTY_TYPE)
    public String getType() {
        return m_type;
    }

    public void setType(final String type) {
        m_type = type;
    }

    @JsonProperty(JSON_PROPERTY_DEPRECATED)
    public boolean isDeprecated() {
        return m_deprecated;
    }

    public void setDeprecated(final boolean deprecated) {
        m_deprecated = deprecated;
    }

    @JsonProperty(JSON_PROPERTY_STREAMABLE)
    public boolean isStreamable() {
        return m_streamable;
    }

    public void setStreamable(final boolean streamable) {
        m_streamable = streamable;
    }

    @JsonProperty(JSON_PROPERTY_IN_PORTS)
    public List<Port> getInPorts() {
        return m_inPorts;
    }

    public void setInPorts(final List<Port> inPorts) {
        m_inPorts = inPorts;
    }

    @JsonProperty(JSON_PROPERTY_OUT_PORTS)
    public List<Port> getOutPorts() {
        return m_outPorts;
    }

    public void setOutPorts(final List<Port> outPorts) {
        m_outPorts = outPorts;
    }

    @JsonProperty(JSON_PROPERTY_HAS_DYN_IN_PORTS)
    public boolean isHasDynInPorts() {
        return m_hasDynInPorts;
    }

    public void setHasDynInPorts(final boolean hasDynInPorts) {
        m_hasDynInPorts = hasDynInPorts;
    }

    @JsonProperty(JSON_PROPERTY_HAS_DYN_OUT_PORTS)
    public boolean isHasDynOutPorts() {
        return m_hasDynOutPorts;
    }

    public void setHasDynOutPorts(final boolean hasDynOutPorts) {
        m_hasDynOutPorts = hasDynOutPorts;
    }
}
