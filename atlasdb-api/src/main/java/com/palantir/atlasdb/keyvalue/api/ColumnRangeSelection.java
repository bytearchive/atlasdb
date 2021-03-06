/**
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.keyvalue.api;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.palantir.atlasdb.encoding.PtBytes;

public class ColumnRangeSelection implements Serializable {
    private static final long serialVersionUID = 1L;

    // Inclusive start column name.
    private final byte[] startCol;
    // Exclusive end column name.
    private final byte[] endCol;
    private final int batchHint;

    @JsonCreator
    public ColumnRangeSelection(@JsonProperty("startInclusive") byte[] startCol,
                                @JsonProperty("endExclusive") byte[] endCol,
                                @JsonProperty("batchHint") int batchHint) {
        this.startCol = MoreObjects.firstNonNull(startCol, PtBytes.EMPTY_BYTE_ARRAY);
        this.endCol = MoreObjects.firstNonNull(endCol, PtBytes.EMPTY_BYTE_ARRAY);
        this.batchHint = batchHint;
    }

    public byte[] getStartCol() {
        return startCol;
    }

    public byte[] getEndCol() {
        return endCol;
    }

    public int getBatchHint() {
        return batchHint;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ColumnRangeSelection that = (ColumnRangeSelection) obj;
        return batchHint == that.batchHint
                && Arrays.equals(startCol, that.startCol)
                && Arrays.equals(endCol, that.endCol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(startCol), Arrays.hashCode(endCol), batchHint);
    }

    private static final Pattern deserializeRegex = Pattern.compile("\\s*,\\s*");

    public static ColumnRangeSelection valueOf(String serialized) {
        String[] split = deserializeRegex.split(serialized);
        byte[] startCol = PtBytes.decodeBase64(split[0]);
        byte[] endCol = PtBytes.decodeBase64(split[1]);
        int batchHint = Integer.parseInt(split[2]);
        return new ColumnRangeSelection(startCol, endCol, batchHint);
    }

    @Override
    public String toString() {
        String start = PtBytes.encodeBase64String(startCol);
        String end = PtBytes.encodeBase64String(endCol);
        String batch = String.valueOf(batchHint);
        return Joiner.on(',').join(ImmutableList.of(start, end, batch));
    }
}
