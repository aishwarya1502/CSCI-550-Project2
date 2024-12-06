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
package org.neo4j.values.storable;

import static java.lang.String.format;
import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;
import static org.neo4j.memory.HeapEstimator.sizeOf;

import java.util.Arrays;
import org.neo4j.values.AnyValue;
import org.neo4j.values.SequenceValue;
import org.neo4j.values.ValueMapper;

public final class LongArray extends IntegralArray {
    private static final long SHALLOW_SIZE = shallowSizeOfInstance(LongArray.class);

    private final long[] value;

    LongArray(long[] value) {
        assert value != null;
        this.value = value;
    }

    @Override
    public int length() {
        return value.length;
    }

    @Override
    public long longValue(int index) {
        return value[index];
    }

    @Override
    protected int computeHashToMemoize() {
        return NumberValues.hash(value);
    }

    @Override
    public <T> T map(ValueMapper<T> mapper) {
        return mapper.mapLongArray(this);
    }

    @Override
    public boolean equals(SequenceValue other) {
        if (other instanceof ArrayValue otherArray) {
            return otherArray.equals(value);
        } else {
            return super.equals(other);
        }
    }

    @Override
    public LongValue value(int offset) {
        return Values.longValue(longValue(offset));
    }

    @Override
    public boolean equals(Value other) {
        return other.equals(value);
    }

    @Override
    public boolean equals(byte[] x) {
        return PrimitiveArrayValues.equals(x, value);
    }

    @Override
    public boolean equals(short[] x) {
        return PrimitiveArrayValues.equals(x, value);
    }

    @Override
    public boolean equals(int[] x) {
        return PrimitiveArrayValues.equals(x, value);
    }

    @Override
    public boolean equals(long[] x) {
        return Arrays.equals(value, x);
    }

    @Override
    public boolean equals(float[] x) {
        return PrimitiveArrayValues.equals(value, x);
    }

    @Override
    public boolean equals(double[] x) {
        return PrimitiveArrayValues.equals(value, x);
    }

    @Override
    public <E extends Exception> void writeTo(ValueWriter<E> writer) throws E {
        PrimitiveArrayWriting.writeTo(writer, value);
    }

    @Override
    public long[] asObjectCopy() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public String prettyPrint() {
        return Arrays.toString(value);
    }

    @Override
    @Deprecated
    public long[] asObject() {
        return value;
    }

    @Override
    public String toString() {
        return format("%s%s", getTypeName(), Arrays.toString(value));
    }

    @Override
    public String getTypeName() {
        return "LongArray";
    }

    @Override
    public long estimatedHeapUsage() {
        return SHALLOW_SIZE + sizeOf(value);
    }

    @Override
    public boolean hasCompatibleType(AnyValue value) {
        return value instanceof IntegralValue;
    }

    @Override
    public ArrayValue copyWithAppended(AnyValue added) {
        assert hasCompatibleType(added) : "Incompatible types";
        long[] newArray = Arrays.copyOf(value, value.length + 1);
        newArray[value.length] = ((IntegralValue) added).longValue();
        return new LongArray(newArray);
    }

    @Override
    public ArrayValue copyWithPrepended(AnyValue prepended) {
        assert hasCompatibleType(prepended) : "Incompatible types";
        long[] newArray = new long[value.length + 1];
        System.arraycopy(value, 0, newArray, 1, value.length);
        newArray[0] = ((IntegralValue) prepended).longValue();
        return new LongArray(newArray);
    }

    @Override
    public ValueRepresentation valueRepresentation() {
        return ValueRepresentation.INT64_ARRAY;
    }
}
