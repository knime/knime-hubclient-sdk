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

import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port description used in search result icons.
 *
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings({"java:S1176", "MissingJavadoc"})
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
    public Port(@JsonProperty(value = JSON_PROPERTY_COLOR, required = true) final String color,
        @JsonProperty(value = JSON_PROPERTY_OPTIONAL, required = true) final Boolean optional,
        @JsonProperty(value = JSON_PROPERTY_DATA_TYPE, required = true) final String dataType,
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
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getName() {
        return Optional.ofNullable(m_name);
    }

    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getDescription() {
        return Optional.ofNullable(m_description);
    }

    @JsonProperty(JSON_PROPERTY_OBJECT_CLASS)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getObjectClass() {
        return Optional.ofNullable(m_objectClass);
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
