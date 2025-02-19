/*
 * ------------------------------------------------------------------------
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
 * -------------------------------------------------------------------
 *
 * History
 *   Nov 6, 2024 (magnus): created
 */

package org.knime.hub.client.sdk.ent;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * POJO representing a upload target.
 * 
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UploadTarget {

    private static final String JSON_PROPERTY_METHOD = "method";
    private final String m_method;
    
    private static final String JSON_PROPERTY_URL = "url";
    private final URL m_url;
    
    private static final String JSON_PROPERTY_HEADER = "header";
    private final Map<String, List<String>> m_header;

    @JsonCreator
    private UploadTarget(
            @JsonProperty(value = JSON_PROPERTY_METHOD, required = true) String method,
            @JsonProperty(value = JSON_PROPERTY_URL, required = true) URL url,
            @JsonProperty(value = JSON_PROPERTY_HEADER, required = true) Map<String, List<String>> header) {
        this.m_method = method;
        this.m_url = url;
        this.m_header = header;
    }

    /**
     * Retrieves the HTTP method of the upload instruction.
     * 
     * @return itemContentType
     */
    @JsonProperty(JSON_PROPERTY_METHOD)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getMethod() {
        return m_method;
    }
    
    /**
     * Retrieves the url of the upload instruction.
     * 
     * @return itemContentType
     */
    @JsonProperty(JSON_PROPERTY_URL)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public URL getUrl() {
        return m_url;
    }
    
    /**
     * Retrieves the header of the upload instruction.
     * 
     * @return itemContentType
     */
    @JsonProperty(JSON_PROPERTY_HEADER)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Map<String, List<String>> getHeader() {
        return m_header;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var uploadTarget = (UploadTarget) o;
        return Objects.equals(this.m_method, uploadTarget.m_method)
                && Objects.equals(this.m_url, uploadTarget.m_url)
                && Objects.equals(this.m_header, uploadTarget.m_header);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_method, m_url, m_header);
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
