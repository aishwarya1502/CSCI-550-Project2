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
package org.neo4j.kernel.api.impl.schema.vector;

import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.schema.IndexConfig;
import org.neo4j.kernel.api.vector.VectorSimilarityFunction;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Value;

public class VectorUtils {

    public static int vectorDimensionsFrom(IndexConfig config) {
        final var setting = IndexSetting.vector_Dimensions();
        final var dimensions =
                VectorUtils.<IntegralValue>getExpectedFrom(config, setting).intValue();
        if (dimensions <= 0) {
            throw new IllegalArgumentException(
                    "Invalid %s provided.".formatted(IndexConfig.class.getSimpleName()),
                    new AssertionError("'%s' is expected to be positive. Provided: %d"
                            .formatted(setting.getSettingName(), dimensions)));
        }
        return dimensions;
    }

    public static VectorSimilarityFunction vectorSimilarityFunctionFrom(
            VectorIndexVersion version, IndexConfig config) {
        try {
            return version.similarityFunction(
                    VectorUtils.<TextValue>getExpectedFrom(config, IndexSetting.vector_Similarity_Function())
                            .stringValue());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid %s provided.".formatted(IndexConfig.class.getSimpleName()), e);
        }
    }

    private static <T extends Value> T getExpectedFrom(IndexConfig config, IndexSetting setting) {
        final var name = setting.getSettingName();
        return config.getOrThrow(
                name,
                () -> new IllegalArgumentException(
                        "Invalid %s provided.".formatted(IndexConfig.class.getSimpleName()),
                        new AssertionError("'%s' is expected to have been set".formatted(name))));
    }

    private VectorUtils() {}
}
