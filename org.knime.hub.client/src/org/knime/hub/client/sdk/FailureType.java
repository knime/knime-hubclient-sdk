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
 *   10 May 2025 (leonard.woerteler): created
 */
package org.knime.hub.client.sdk;

/**
 * Type of a {@link FailureValue} returned by the SDK.
 *
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public enum FailureType {

    /** The requested item is not contained in the parent group. */
    DOWNLOAD_ITEM_NOT_FOUND,
    /** The item is not of a downloadable type. */
    DOWNLOAD_ITEM_UNKNOWN_TYPE,
    /** The user doesn'T have download permissions on the item. */
    DOWNLOAD_ITEM_NOT_DOWNLOADABLE,
    /** The hub failed to prepare the item for download. */
    DOWNLOAD_ITEM_PREP_FAILED,
    /** The item could not be imported into the target folder. */
    DOWNLOAD_ITEM_IMPORT_FAILED,
    /** THe download stream could not be opened. */
    DOWNLOAD_STREAM_OPEN_FAILED,

    /** A part of the multi-part upload could not be uploaded. */
    UPLOAD_PART_FAILED,
    /** The upload was aborted while processing on the Hub side. */
    UPLOAD_ABORTED_BY_HUB,
    /** There was a problem processing the uploaded item on the Hub side. */
    UPLOAD_PROCESSING_FAILED,
    /** There was a problem zipping and sending a workflow to Hub. */
    UPLOAD_OF_WORKFLOW_FAILED,
    /** An upload part was unreadable while trying to compute an MD5 hash. */
    UPLOAD_PART_UNREADABLE,
    /** The item to be uploaded is larger than the Hub's upload limit. */
    UPLOAD_LIMIT_EXCEEDED,
    /** Upload of a part was aborted after exhausting its retries. */
    PART_UPLOAD_EXHAUSTED_RETRIES,
    /** The upload connection could not be created.
     * @since 0.1*/
    UPLOAD_CONNECTION_CREATION_FAILED,
    /** The upload stream could not be created.
     * @since 0.1*/
    UPLOAD_STREAM_CREATION_FAILED,

    /** A Hub REST call returned a non-successful response code. */
    HUB_FAILURE_RESPONSE,
    /** There were problems reaching Hub. */
    NETWORK_CONNECTIVITY_ERROR,
    /** A redirect failed, potentially because of wrong proxy/firewall settings. */
    REDIRECT_FAILED,
    /** The user was logged out of the Hub. */
    COULD_NOT_AUTHORIZE,
    /** An unexpected error occurred. */
    UNEXPECTED_ERROR,
}
