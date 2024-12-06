/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.server.security.systemgraph.versions;

import static org.neo4j.server.security.systemgraph.UserSecurityGraphComponentVersion.COMMUNITY_SECURITY_41;

import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.security.AbstractSecurityLog;
import org.neo4j.logging.Log;
import org.neo4j.server.security.auth.UserRepository;

/**
 * This is the UserSecurityComponent version for Neo4j 4.1
 */
public class CommunitySecurityComponentVersion_2_41 extends SupportedCommunitySecurityComponentVersion {
    private final KnownCommunitySecurityComponentVersion previous;

    public CommunitySecurityComponentVersion_2_41(
            Log debugLog,
            AbstractSecurityLog securityLog,
            UserRepository userRepository,
            KnownCommunitySecurityComponentVersion previous) {
        super(COMMUNITY_SECURITY_41, userRepository, debugLog, securityLog);
        this.previous = previous;
    }

    @Override
    public void upgradeSecurityGraph(Transaction tx, int fromVersion) throws Exception {
        if (fromVersion < version) {
            previous.upgradeSecurityGraph(tx, fromVersion);
            // This version introduced the Version node
            this.setVersionProperty(tx, version);
        }
    }

    @Override
    public void upgradeSecurityGraphSchema(Transaction tx, int fromVersion) {}
}
