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
 *   Apr 22, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Implementation of a application/problem+json response mostly compatible with RFC 9457 standard.
 *
 * @author Magnus Gohm, KNIME AG, KOnstanz, Germany
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RFC9457 {

    private static final String JSON_PROPERTY_TYPE = "type";
    private final String m_type;

    private static final String JSON_PROPERTY_STATUS = "status";
    private final String m_status;

    private static final String JSON_PROPERTY_TITLE = "title";
    private final String m_title;

    private static final String JSON_PROPERTY_INSTANCE = "instance";
    private final String m_instance;

    private static final String JSON_PROPERTY_DETAILS = "details";
    private final List<String> m_details;

    private static final String JSON_PROPERTY_CODE = "code";
    private final String m_code;

    private final Map<String, Object> m_additionalProperties = new HashMap<>();

    /**
     * Problem JSON error according to standard RFC9457.
     *
     * @param type the type
     * @param status the status
     * @param title the title
     * @param instance the instance
     * @param details the details
     * @param code the code
     */
    @JsonCreator
    public RFC9457(
            @JsonProperty(value = JSON_PROPERTY_TYPE, required = false) final String type,
            @JsonProperty(value = JSON_PROPERTY_STATUS, required = false) final String status,
            @JsonProperty(value = JSON_PROPERTY_TITLE, required = true) final String title,
            @JsonProperty(value = JSON_PROPERTY_INSTANCE, required = false) final String instance,
            @JsonProperty(value = JSON_PROPERTY_DETAILS, required = false) final List<String> details,
            @JsonProperty(value = JSON_PROPERTY_CODE, required = false) final String code) {
        m_type = type;
        m_status = status;
        m_title = title;
        m_instance = instance;
        m_details = details;
        m_code = code;
    }

    /**
     * Retrieves the optional type
     *
     * @return type
     */
    @JsonProperty(JSON_PROPERTY_TYPE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Optional<String> getType() {
        return Optional.ofNullable(m_type);
    }

    /**
     * Retrieves the optional status
     *
     * @return status
     */
    @JsonProperty(JSON_PROPERTY_STATUS)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Optional<String> getStatus() {
        return Optional.ofNullable(m_status);
    }

    /**
     * Retrieves the required title
     *
     * @return title
     */
    @JsonProperty(JSON_PROPERTY_TITLE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getTitle() {
        return m_title;
    }

    /**
     * Retrieves the optional instance
     *
     * @return instance
     */
    @JsonProperty(JSON_PROPERTY_INSTANCE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Optional<String> getInstance() {
        return Optional.ofNullable(m_instance);
    }

    /**
     * Retrieves the optional details
     *
     * @return details
     */
    @JsonProperty(JSON_PROPERTY_DETAILS)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public List<String> getDetails() {
        return Optional.ofNullable(m_details).orElseGet(Collections::emptyList);
    }

    /**
     * Retrieves the optional code
     *
     * @return code
     */
    @JsonProperty(JSON_PROPERTY_CODE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Optional<String> getCode() {
        return Optional.ofNullable(m_code);
    }

    /**
     * Catch-all setter for additional information in the problem object.
     *
     * @param key additional object key
     * @param value additional object value
     */
    @JsonAnySetter
    public void add(final String key, final Object value) {
        m_additionalProperties.put(key, value);
    }

    /**
     * Additional information in the problem object.
     *
     * @return map containing all additional entries in the JSON object representing the problem
     */
    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return Collections.unmodifiableMap(m_additionalProperties);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var rfc9457 = (RFC9457) o;
        return Objects.equals(this.m_type, rfc9457.m_type)
                && Objects.equals(this.m_status, rfc9457.m_status)
                && Objects.equals(this.m_title, rfc9457.m_title)
                && Objects.equals(this.m_instance, rfc9457.m_instance)
                && Objects.equals(this.m_details, rfc9457.m_details)
                && Objects.equals(this.m_code, rfc9457.m_code)
                && Objects.equals(this.m_additionalProperties, rfc9457.m_additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_type, m_status, m_title, m_instance, m_details, m_code, m_additionalProperties);
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperUtil.getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON: ", e);
        }
    }
}
