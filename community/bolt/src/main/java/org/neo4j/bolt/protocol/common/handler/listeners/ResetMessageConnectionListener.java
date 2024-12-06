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
package org.neo4j.bolt.protocol.common.handler.listeners;

import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.bolt.protocol.common.connector.connection.listener.ConnectionListener;
import org.neo4j.bolt.protocol.common.handler.messages.GoodbyeMessageHandler;
import org.neo4j.bolt.protocol.common.handler.messages.ResetMessageHandler;
import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.memory.HeapEstimator;

public class ResetMessageConnectionListener implements ConnectionListener {
    public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(ResetMessageHandler.class);
    private final InternalLogProvider logger;
    private final Connection connection;

    private ResetMessageHandler resetMessageHandler;

    public ResetMessageConnectionListener(InternalLogProvider logger, Connection connection) {
        this.logger = logger;
        this.connection = connection;
    }

    @Override
    public void onLogon(LoginContext ctx) {
        connection.memoryTracker().allocateHeap(ResetMessageHandler.SHALLOW_SIZE);

        resetMessageHandler = new ResetMessageHandler(logger);
        connection
                .channel()
                .pipeline()
                .addBefore(GoodbyeMessageHandler.HANDLER_NAME, "resetMessageHandler", resetMessageHandler);
    }

    @Override
    public void onLogoff() {
        connection.channel().pipeline().remove(resetMessageHandler);
        resetMessageHandler = null;
    }
}
