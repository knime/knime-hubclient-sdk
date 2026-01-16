package org.knime.hub.client.sdk.ent.search;

import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
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
    private final String m_color;

    private static final String JSON_PROPERTY_OPTIONAL = "optional";
    private final Boolean m_optional;

    private static final String JSON_PROPERTY_DATA_TYPE = "dataType";
    private final String m_dataType;

    private static final String JSON_PROPERTY_NAME = "name";
    private final String m_name;

    private static final String JSON_PROPERTY_DESCRIPTION = "description";
    private final String m_description;

    private static final String JSON_PROPERTY_OBJECT_CLASS = "objectClass";
    private final String m_objectClass;

    @JsonCreator
    private Port(@JsonProperty(JSON_PROPERTY_COLOR) final String color,
        @JsonProperty(JSON_PROPERTY_OPTIONAL) final Boolean optional,
        @JsonProperty(JSON_PROPERTY_DATA_TYPE) final String dataType,
        @JsonProperty(JSON_PROPERTY_NAME) final String name,
        @JsonProperty(JSON_PROPERTY_DESCRIPTION) final String description,
        @JsonProperty(JSON_PROPERTY_OBJECT_CLASS) final String objectClass) {
        m_color = color;
        m_optional = optional;
        m_dataType = dataType;
        m_name = name;
        m_description = description;
        m_objectClass = objectClass;
    }

    @JsonProperty(JSON_PROPERTY_COLOR)
    public String getColor() {
        return m_color;
    }

    @JsonProperty(JSON_PROPERTY_OPTIONAL)
    public Boolean getOptional() {
        return m_optional;
    }

    @JsonProperty(JSON_PROPERTY_DATA_TYPE)
    public String getDataType() {
        return m_dataType;
    }

    @JsonProperty(JSON_PROPERTY_NAME)
    public String getName() {
        return m_name;
    }

    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    public String getDescription() {
        return m_description;
    }

    @JsonProperty(JSON_PROPERTY_OBJECT_CLASS)
    public String getObjectClass() {
        return m_objectClass;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (Port)o;
        return Objects.equals(m_color, that.m_color)
            && Objects.equals(m_optional, that.m_optional)
            && Objects.equals(m_dataType, that.m_dataType)
            && Objects.equals(m_name, that.m_name)
            && Objects.equals(m_description, that.m_description)
            && Objects.equals(m_objectClass, that.m_objectClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_color, m_optional, m_dataType, m_name, m_description, m_objectClass);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
