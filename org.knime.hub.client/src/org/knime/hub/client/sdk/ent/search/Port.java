package org.knime.hub.client.sdk.ent.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port description used in search result icons.
 *
 * @since 1.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Port {

    private static final String JSON_PROPERTY_COLOR = "color";
    private static final String JSON_PROPERTY_OPTIONAL = "optional";
    private static final String JSON_PROPERTY_DATA_TYPE = "dataType";
    private static final String JSON_PROPERTY_NAME = "name";
    private static final String JSON_PROPERTY_DESCRIPTION = "description";
    private static final String JSON_PROPERTY_OBJECT_CLASS = "objectClass";

    private String m_color;
    private Boolean m_optional;
    private String m_dataType;
    private String m_name;
    private String m_description;
    private String m_objectClass;

    @JsonProperty(JSON_PROPERTY_COLOR)
    public String getColor() {
        return m_color;
    }

    public void setColor(final String color) {
        m_color = color;
    }

    @JsonProperty(JSON_PROPERTY_OPTIONAL)
    public Boolean getOptional() {
        return m_optional;
    }

    public void setOptional(final Boolean optional) {
        m_optional = optional;
    }

    @JsonProperty(JSON_PROPERTY_DATA_TYPE)
    public String getDataType() {
        return m_dataType;
    }

    public void setDataType(final String dataType) {
        m_dataType = dataType;
    }

    @JsonProperty(JSON_PROPERTY_NAME)
    public String getName() {
        return m_name;
    }

    public void setName(final String name) {
        m_name = name;
    }

    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    public String getDescription() {
        return m_description;
    }

    public void setDescription(final String description) {
        m_description = description;
    }

    @JsonProperty(JSON_PROPERTY_OBJECT_CLASS)
    public String getObjectClass() {
        return m_objectClass;
    }

    public void setObjectClass(final String objectClass) {
        m_objectClass = objectClass;
    }
}
