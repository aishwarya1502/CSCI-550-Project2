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
package org.neo4j.server.http.cypher.format.jolt.v2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.neo4j.server.http.cypher.format.jolt.JoltRelationship;
import org.neo4j.server.http.cypher.format.jolt.Sigil;

/**
 * Custom relationship serializer to flip the relationship sigil when use with {@link JoltPathSerializer} to allow flowing paths.
 */
final class JoltRelationshipReversedSerializerV2 extends StdSerializer<JoltRelationship> {
    JoltRelationshipReversedSerializerV2() {
        super(JoltRelationship.class);
    }

    @Override
    public void serialize(JoltRelationship relationship, JsonGenerator generator, SerializerProvider provider)
            throws IOException {
        generator.writeStartObject(relationship);
        generator.writeFieldName(Sigil.RELATIONSHIP_REVERSED.getValue());

        generator.writeStartArray();

        generator.writeString(relationship.getElementId());

        generator.writeString(relationship.getStartNodeElementId());

        generator.writeString(relationship.getType().name());

        generator.writeString(relationship.getEndNodeElementId());

        var properties = Optional.ofNullable(relationship.getAllProperties()).orElseGet(Map::of);
        generator.writeStartObject();

        for (var entry : properties.entrySet()) {
            generator.writeFieldName(entry.getKey());
            generator.writeObject(entry.getValue());
        }

        generator.writeEndObject();

        generator.writeEndArray();
        generator.writeEndObject();
    }
}
