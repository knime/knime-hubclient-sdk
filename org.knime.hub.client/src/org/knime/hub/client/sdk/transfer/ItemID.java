/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Jun 15, 2024 by leonard.woerteler
 */
package org.knime.hub.client.sdk.transfer;

import org.knime.core.node.util.CheckUtils;

/**
 * ID of an item in the Hub repository.
 *
 * @param id string representation of the ID
 * @author Leonard Wörteler, KNIME GmbH, Konstanz, Germany
 */
public record ItemID(String id) {
    /**
     * @param id string representation of the ID, must start with an asterisk
     */
    public ItemID {
        CheckUtils.checkArgument(CheckUtils.checkArgumentNotNull(id).startsWith("*"),
            "Item IDs must start with an asterisk '*', found '%s'", id);
    }
}
