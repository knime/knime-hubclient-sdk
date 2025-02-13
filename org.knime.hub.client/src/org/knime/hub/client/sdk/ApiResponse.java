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

package org.knime.hub.client.sdk;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.ws.rs.core.EntityTag;

/**
 * API response class which combines response details from {@link ApiClient} and
 * response entities from the API class.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public class ApiResponse<R> {

    /** Response information */
    private Map<String, List<Object>> m_headers;
    private int m_statusCode;
    private String m_statusMessage;
    private Optional<EntityTag> m_etag;
    
    /** Response result */
    private Result<R> m_result;

    /**
     * Creates an API response objects which combines the response entity with
     * request and response information.
     * 
     * @param responseHeaders the response headers
     * @param responseStatusCode the response status code
     * @param responseStatusMessage the response status message
     * @param responseResult {@link Result<R>} the response result
     */
    public ApiResponse(final Map<String, List<Object>> responseHeaders, final int responseStatusCode, 
            final String responseStatusMessage, final EntityTag etag, final Result<R> responseResult) {
        this.m_headers = responseHeaders;
        this.m_statusCode = responseStatusCode;
        this.m_statusMessage = responseStatusMessage;
        this.m_etag = Optional.ofNullable(etag);
        this.m_result = responseResult;
    }

    /**
     * Creates a empty API response object.
     */
    public ApiResponse() {}

    /**
     * Retrieves the response headers.
     * 
     * @return the response headers
     */
    public Map<String, List<Object>> getHeaders() {
        return m_headers;
    }

    /**
     * Retrieves the response status code.
     * 
     * @return the response status code
     */
    public int getStatusCode() {
        return m_statusCode;
    }

    /**
     * Retrieves the response status message.
     * 
     * @return the response status message
     */
    public String getStatusMessage() {
        return m_statusMessage;
    }
    
    /**
     * Retrieves the response entity tag.
     * 
     * @return the response etag
     */
    public Optional<EntityTag> getETag() {
        return m_etag;
    }

    /**
     * Retrieves the response result.
     * 
     * @return {@link Result<R>} the response result
     */
    public Result<R> getResult() {
        return m_result;
    }
    
}
