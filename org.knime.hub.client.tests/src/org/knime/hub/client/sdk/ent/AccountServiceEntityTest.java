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
 *   May 20, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.knime.hub.client.sdk.AbstractTest;
import org.knime.hub.client.sdk.ent.account.AccountIdentity;
import org.knime.hub.client.sdk.ent.account.AccountIdentity.AccountIdentityType;
import org.knime.hub.client.sdk.ent.account.Billboard;
import org.knime.hub.client.sdk.ent.account.Billboard.AuthenticationType;
import org.knime.hub.client.sdk.ent.account.UserAccount;
import org.knime.hub.client.sdk.testing.TestUtil.EntityFolders;

/**
 * Tests creation via Jackson deserialization of the account service entities
 * in {@link org.knime.hub.client.sdk.ent.account}.
 *
 * This class is in another package on purpose to test from outside package scope.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
class AccountServiceEntityTest extends AbstractTest {

    @Test
    void testCreateBillboardWithoutAuth() throws IOException, URISyntaxException {
        final var billboard = load(EntityFolders.ACCOUNT_ENTITIES, "billboard.json", Billboard.class);
        assertEquals("KNIME-Hub", billboard.getMountId(), "Unexpected mount ID");
        assertTrue(billboard.isEnableResetOnUploadCheckbox().isEmpty(), "Unexpected enabled");
        assertTrue(billboard.getOAuthInformation().isEmpty(), "Unexpected OAuth information");
        assertTrue(billboard.isForceResetOnUpload().isEmpty(), "Unexpected enabled");
        assertTrue(billboard.getClientExplorerFetchTimeout().isEmpty(), "Unexpected fetch timeout");
        assertTrue(billboard.getClientExplorerFetchInterval().isEmpty(), "Unexpected fetch interval");

        final var masonControls = billboard.getMasonControls();
        assertTrue(!masonControls.isEmpty(), "Expected mason controls");
        assertTrue(masonControls.containsKey("self"), "Expected self control");
        assertEquals("https://api.hub.com/knime/rest", masonControls.get("self").getHref(),
            "Unexpected href for self control");
        assertEquals("GET", masonControls.get("self").getMethod(), "Unexpected method for self control");

        assertEquals(AuthenticationType.OAuth, billboard.getPreferredAuthType(), "Unexpected preferred auth type");

        final var version = billboard.getVersion();
        assertEquals(5, version.getMajor(), "Unexpected major version");
        assertEquals(0, version.getMinor(), "Unexpected minor version");
        assertTrue(version.getQualifier().isBlank(), "Unexpected qualifier");
        assertEquals(0, version.getRevision(), "Unexpected revision");
    }

    @Test
    void testCreateBillboardWithAuth() throws IOException, URISyntaxException {
        final var billboard = load(EntityFolders.ACCOUNT_ENTITIES, "billboardWithAuth.json", Billboard.class);
        assertEquals("KNIME-Hub", billboard.getMountId(), "Unexpected mount ID");

        assertTrue(billboard.isEnableResetOnUploadCheckbox().isPresent(), "Expected enbaled reset on upload checkbox");
        assertTrue(billboard.isEnableResetOnUploadCheckbox().get(), "Unexpected disabled");

        final var oAuthInfo = billboard.getOAuthInformation();
        assertTrue(billboard.getOAuthInformation().isPresent(), "Expected OAuth information");
        assertTrue(oAuthInfo.get().getTokenEndpoint().isPresent(), "Expected token endpoint");
        assertEquals("https://auth.hub.com/auth/realms/knime/protocol/openid-connect/token",
            oAuthInfo.get().getTokenEndpoint().get().toString(), "Unexpected token endpoint");
        assertTrue(oAuthInfo.get().getClientId().isPresent(), "Expected client ID");
        assertEquals("analytics-platform", oAuthInfo.get().getClientId().get(), "Unexpected client ID");
        assertTrue(oAuthInfo.get().getAuthorizationEndpoint().isPresent(), "Expected authorization endpoint");
        assertEquals("https://auth.hub.com/auth/realms/knime/protocol/openid-connect/auth",
            oAuthInfo.get().getAuthorizationEndpoint().get().toString(), "Unexpected authorization endpoint");

        assertTrue(billboard.isForceResetOnUpload().isPresent(), "Expected force reset on upload");
        assertTrue(billboard.isForceResetOnUpload().get(), "Unexpected disabled");

        assertTrue(billboard.getClientExplorerFetchTimeout().isPresent(), "Expected fetch timeout");
        assertEquals(billboard.getClientExplorerFetchTimeout().get(),
            Duration.ofMinutes(3), "Unexpected fetch timeout");
        assertTrue(billboard.getClientExplorerFetchInterval().isPresent(), "Expected fetch interval");
        assertEquals(billboard.getClientExplorerFetchInterval().get(),
            Duration.ofSeconds(2), "Unexpected fetch interval");

        final var masonControls = billboard.getMasonControls();
        assertTrue(!masonControls.isEmpty(), "Expected mason controls");
        assertTrue(masonControls.containsKey("self"), "Expected self control");
        assertEquals("https://api.hub.com/knime/rest", masonControls.get("self").getHref(),
            "Unexpected href for self control");
        assertEquals("GET", masonControls.get("self").getMethod(), "Unexpected method for self control");

        assertEquals(AuthenticationType.OAuth, billboard.getPreferredAuthType(), "Unexpected preferred auth type");

        final var version = billboard.getVersion();
        assertEquals(5, version.getMajor(), "Unexpected major version");
        assertEquals(0, version.getMinor(), "Unexpected minor version");
        assertTrue(version.getQualifier().isBlank(), "Unexpected qualifier");
        assertEquals(0, version.getRevision(), "Unexpected revision");
    }

    @Test
    void testAccountIdentity() throws IOException, URISyntaxException {
        var accounntIdenity = load(EntityFolders.ACCOUNT_ENTITIES,
            "accountIdentityWithoutTeam.json", AccountIdentity.class);
        var userAccountIdentity = (UserAccount)accounntIdenity;
        assertEquals("account:user:395acdbe-324d-4da4-800e-2f2a3a142b0d",
            userAccountIdentity.getId(), "Unexpected account ID");
        assertEquals(AccountIdentityType.USER, userAccountIdentity.getType(), "Unexpected account type");
        assertEquals("space_explorer_user", userAccountIdentity.getName(), "Unexpected account name");
        assertTrue(userAccountIdentity.getTeams().isEmpty(), "Unexpected account teams");

        accounntIdenity = load(EntityFolders.ACCOUNT_ENTITIES, "accountIdentityWithTeam.json", AccountIdentity.class);
        userAccountIdentity = (UserAccount)accounntIdenity;
        assertEquals("account:user:395acdbe-324d-4da4-800e-2f2a3a142b0d",
            userAccountIdentity.getId(), "Unexpected account ID");
        assertEquals(AccountIdentityType.USER, userAccountIdentity.getType(), "Unexpected account type");
        assertEquals("space_explorer_user", userAccountIdentity.getName(), "Unexpected account name");
        final var teams = userAccountIdentity.getTeams();
        assertTrue(!teams.isEmpty(), "Expected teams");
        final var team = teams.get(0);
        assertEquals("Space Explorer Team", team.getName(), "Unexpected team name");
    }

}
