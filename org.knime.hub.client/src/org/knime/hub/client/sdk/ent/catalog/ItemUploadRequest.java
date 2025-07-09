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

package org.knime.hub.client.sdk.ent.catalog;

import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing the request to upload a single item.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ItemUploadRequest {

    private static final String JSON_PROPERTY_ITEM_CONTENT_TYPE = "itemContentType";
    private final String m_itemContentType;

    private static final String JSON_PROPERTY_INITIAL_PART_COUNT = "initialPartCount";
    private final Integer m_initialPartCount;

    /**
     * Item upload request body
     *
     * @param itemContentType the content type of the uploaded item
     * @param initialPartCount the initial part count of the uploaded item
     */
    @JsonCreator
    public ItemUploadRequest(
            @JsonProperty(value = JSON_PROPERTY_ITEM_CONTENT_TYPE, required = true) final String itemContentType,
            @JsonProperty(value = JSON_PROPERTY_INITIAL_PART_COUNT) final Integer initialPartCount) {
        this.m_itemContentType = itemContentType;
        this.m_initialPartCount = initialPartCount;
    }

    /**
     * Retrieves the media type of the item to upload
     *
     * @return itemContentType
     */
    @JsonProperty(JSON_PROPERTY_ITEM_CONTENT_TYPE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getItemContentType() {
        return m_itemContentType;
    }

    /**
     * Retrieves the number of initial upload parts (pre-signed URLs) minimum: 0
     *
     * @return initialPartCount
     */
    @JsonProperty(JSON_PROPERTY_INITIAL_PART_COUNT)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Integer getInitialPartCount() {
        return m_initialPartCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var itemUploadRequest = (ItemUploadRequest) o;
        return Objects.equals(this.m_itemContentType, itemUploadRequest.m_itemContentType)
                && Objects.equals(this.m_initialPartCount, itemUploadRequest.m_initialPartCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_itemContentType, m_initialPartCount);
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
