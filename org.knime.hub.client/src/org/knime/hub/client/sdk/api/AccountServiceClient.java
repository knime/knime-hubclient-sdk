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
 *   Apr 24, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.api;

import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiClient.Method;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.ent.account.AccountIdentity;
import org.knime.hub.client.sdk.ent.account.Billboard;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

/**
 * Account Service client for KNIME Hub.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public final class AccountServiceClient {

    /* API Paths */
    private static final String BILLBOARD_API_PATH = "knime/rest";
    private static final String ACCOUNTS_API_PATH = "accounts";

    /* Path pieces */
    private static final String PATH_PIECE_IDENTITY = "identity";

    /* Return types */
    private static final GenericType<Billboard> BILLBOARD = new GenericType<Billboard>() {};
    private static final GenericType<AccountIdentity> ACCOUNT_IDENTITY = new GenericType<AccountIdentity>() {};

    private final @NotOwning ApiClient m_apiClient;

    /**
     * Create the {@link AccountServiceClient} given an {@link ApiClient}
     *
     * @param apiClient the {@link ApiClient}
     */
    public AccountServiceClient(final @NotOwning ApiClient apiClient) {
        m_apiClient = apiClient;
    }

    /**
     * Retrieves the billboard information of the hub instance.
     *
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     */
    public ApiResponse<Billboard> getBillboard(final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        final var requestPath = IPath.forPosix(BILLBOARD_API_PATH);

        return m_apiClient.createApiRequest() //
            .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
            .withHeaders(additionalHeaders) //
            .invokeAPI(requestPath, Method.GET, null, BILLBOARD);
    }

    /**
     * Returns the information of the account doing the request.
     *
     * @param additionalHeaders Map of additional headers
     * @return {@link ApiResponse}
     *
     * @throws HubFailureIOException if an I/O error occurred
     * @since 0.1
     */
    public ApiResponse<AccountIdentity> getAccountIdentity(final Map<String, String> additionalHeaders)
        throws HubFailureIOException {
        final var requestPath = IPath.forPosix(ACCOUNTS_API_PATH).append(PATH_PIECE_IDENTITY);

        return m_apiClient.createApiRequest() //
                .withAcceptHeaders(MediaType.APPLICATION_JSON_TYPE, ApiClient.APPLICATION_PROBLEM_JSON_TYPE) //
                .withHeaders(additionalHeaders) //
                .invokeAPI(requestPath, Method.GET, null, ACCOUNT_IDENTITY);
    }

}
