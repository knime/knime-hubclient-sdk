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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing the ID of the prepared upload, together with a specification of the request
 * to actually upload an item.
 * 
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ItemUploadInstructions {

    private static final String JSON_PROPERTY_UPLOAD_ID = "uploadId";
    private final String m_uploadId;

    private static final String JSON_PROPERTY_UPLOAD_PARTS = "parts";
    private final Optional<Map<Integer, UploadTarget>> m_parts;


    @JsonCreator
    private ItemUploadInstructions(
            @JsonProperty(value = JSON_PROPERTY_UPLOAD_ID) String uploadId,
            @JsonProperty(value = JSON_PROPERTY_UPLOAD_PARTS) 
            Map<Integer, UploadTarget> parts) {
        this.m_uploadId = uploadId;
        this.m_parts = Optional.ofNullable(parts);
    }

    /**
     * Retrieves the ID of the upload; allows clients to track the status of the upload
     * process.
     * 
     * @return uploadId
     */
    @JsonProperty(JSON_PROPERTY_UPLOAD_ID)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getUploadId() {
        return m_uploadId;
    }

    /**
     * Retrieves the upload instructions per part upload.
     * 
     * @return uploadUrl
     */
    @JsonProperty(JSON_PROPERTY_UPLOAD_PARTS)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Optional<Map<Integer, UploadTarget>> getParts() {
        return m_parts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var itemPartUploadInstructions = (ItemUploadInstructions) o;
        return Objects.equals(this.m_uploadId, itemPartUploadInstructions.m_uploadId)
                && Objects.equals(this.m_parts, itemPartUploadInstructions.m_parts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_uploadId, m_parts);
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
