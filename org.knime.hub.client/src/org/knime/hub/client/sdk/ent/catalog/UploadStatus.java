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

import java.time.Instant;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing the status of an asynchronous upload.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UploadStatus {

    private static final String JSON_PROPERTY_UPLOAD_ID = "uploadId";
    private final String m_uploadId;

    private static final String JSON_PROPERTY_INITIATOR_ACCOUNT_ID = "initiatorAccountId";
    private final String m_initiatorAccountId;

    /**
     * Upload status enum.
     */
    public enum StatusEnum {
        /** Upload Prepared */
        PREPARED("PREPARED"),
        /** Waiting on upload analysis */
        ANALYSIS_PENDING("ANALYSIS_PENDING"),
        /** Upload completed */
        COMPLETED("COMPLETED"),
        /** Upload failed */
        FAILED("FAILED"),
        /** Upload aborted */
        ABORTED("ABORTED");

        private String m_value;

        StatusEnum(final String value) {
            this.m_value = value;
        }

        @JsonValue
        private String getValue() {
            return m_value;
        }

        @Override
        public String toString() {
            return String.valueOf(m_value);
        }

        @JsonCreator
        private static StatusEnum fromValue(final String value) {
            for (StatusEnum b : StatusEnum.values()) {
                if (b.m_value.equals(value)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }

    }

    private static final String JSON_PROPERTY_STATUS = "status";
    private final StatusEnum m_status;

    private static final String JSON_PROPERTY_STATUS_MESSAGE = "statusMessage";
    private final String m_statusMessage;

    private static final String JSON_PROPERTY_LAST_UPDATED = "lastUpdated";
    private final Instant m_lastUpdated;

    private static final String JSON_PROPERTY_TARGET_CANONICAL_PATH = "targetCanonicalPath";
    private final String m_targetCanonicalPath;

    /**
     * The upload status
     *
     * @param uploadId the ID of the upload
     * @param initiatorAccountId the account ID of the upload initiator
     * @param status the upload status
     * @param statusMessage the upload status message
     * @param lastUpdated the time of the last update
     * @param targetCanonicalPath the canonical path of the target
     */
    @JsonCreator
    public UploadStatus(
            @JsonProperty(value = JSON_PROPERTY_UPLOAD_ID, required = true) final String uploadId,
            @JsonProperty(value = JSON_PROPERTY_INITIATOR_ACCOUNT_ID, required = true) final String initiatorAccountId,
            @JsonProperty(value = JSON_PROPERTY_STATUS, required = true) final StatusEnum status,
            @JsonProperty(value = JSON_PROPERTY_STATUS_MESSAGE, required = true) final String statusMessage,
            @JsonProperty(value = JSON_PROPERTY_LAST_UPDATED, required = true) final Instant lastUpdated,
            @JsonProperty(value = JSON_PROPERTY_TARGET_CANONICAL_PATH, required = true) final String targetCanonicalPath
            ) {
        this.m_uploadId = uploadId;
        this.m_initiatorAccountId = initiatorAccountId;
        this.m_status = status;
        this.m_statusMessage = statusMessage;
        this.m_lastUpdated = lastUpdated;
        this.m_targetCanonicalPath = targetCanonicalPath;
    }

    /**
     * Retrieves the ID of the upload process.
     *
     * @return uploadId
     */
    @JsonProperty(JSON_PROPERTY_UPLOAD_ID)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getUploadId() {
        return m_uploadId;
    }

    /**
     * Retrieves the account ID of the user who initiated the upload process.
     *
     * @return initiatorAccountId
     */
    @JsonProperty(JSON_PROPERTY_INITIATOR_ACCOUNT_ID)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getInitiatorAccountId() {
        return m_initiatorAccountId;
    }

    /**
     * Retrieves the status of the upload process.
     *
     * @return status
     */
    @JsonProperty(JSON_PROPERTY_STATUS)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public StatusEnum getStatus() {
        return m_status;
    }

    /**
     * Retrieves a human readable message describing the upload process status.
     *
     * @return statusMessage
     */
    @JsonProperty(JSON_PROPERTY_STATUS_MESSAGE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getStatusMessage() {
        return m_statusMessage;
    }

    /**
     * Retrieves the date/time the status was last updated.
     *
     * @return lastUpdated
     */
    @JsonProperty(JSON_PROPERTY_LAST_UPDATED)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Instant getLastUpdated() {
        return m_lastUpdated;
    }

    /**
     * Retrieves the target location of the upload process, e.g.
     * the canonical path the item is being uploaded to
     *
     * @return targetCanonicalPath
     */
    @JsonProperty(JSON_PROPERTY_TARGET_CANONICAL_PATH)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getTargetCanonicalPath() {
        return m_targetCanonicalPath;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var uploadStatus = (UploadStatus) o;
        return Objects.equals(this.m_uploadId, uploadStatus.m_uploadId)
                && Objects.equals(this.m_initiatorAccountId, uploadStatus.m_initiatorAccountId)
                && Objects.equals(this.m_status, uploadStatus.m_status)
                && Objects.equals(this.m_statusMessage, uploadStatus.m_statusMessage)
                && Objects.equals(this.m_lastUpdated, uploadStatus.m_lastUpdated)
                && Objects.equals(this.m_targetCanonicalPath, uploadStatus.m_targetCanonicalPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_uploadId, m_initiatorAccountId, m_status,
                m_statusMessage, m_lastUpdated, m_targetCanonicalPath);
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
