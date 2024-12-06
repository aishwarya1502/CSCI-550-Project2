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
package org.neo4j.server.httpv2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.server.httpv2.response.TypedJsonDriverResultWriter;
import org.neo4j.storageengine.api.TransactionIdStore;

public final class HttpV2ClientUtil {

    public static HttpRequest.Builder baseRequestBuilder(String endpoint, String databaseName) {
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint.replace("{databaseName}", databaseName)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    public static HttpRequest.Builder baseTypedRequestBuilder(String endpoint, String databaseName) {
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint.replace("{databaseName}", databaseName)))
                .header("Content-Type", TypedJsonDriverResultWriter.TYPED_JSON_MIME_TYPE_VALUE)
                .header("Accept", TypedJsonDriverResultWriter.TYPED_JSON_MIME_TYPE_VALUE);
    }

    static HttpResponse<String> simpleRequest(HttpClient client, String endpoint, String database, String requestBody)
            throws IOException, InterruptedException {
        var httpRequest = baseRequestBuilder(endpoint, database)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> simpleRequest(HttpClient client, String endpoint, String requestBody)
            throws IOException, InterruptedException {
        return simpleRequest(client, endpoint, "neo4j", requestBody);
    }

    public static HttpResponse<String> simpleRequest(HttpClient client, String endpoint)
            throws IOException, InterruptedException {
        return simpleRequest(client, endpoint, "neo4j", "{\"statement\": \"RETURN 1\"}");
    }

    static <T> T resolveDependency(DatabaseManagementService database, Class<T> cls) {
        return ((GraphDatabaseAPI) database.database("neo4j"))
                .getDependencyResolver()
                .resolveDependency(cls);
    }

    static long getLastClosedTransactionId(DatabaseManagementService database) {
        var txIdStore = resolveDependency(database, TransactionIdStore.class);
        return txIdStore.getLastClosedTransactionId();
    }

    static String encodedCredentials(String username, String password) {
        String valueToEncode = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    }
}
