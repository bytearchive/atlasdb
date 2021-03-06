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

import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;
import com.palantir.common.annotation.Immutable;

/**
 * Represents a partial row to be used for range requests.
 */
@Immutable
public class Prefix {
    private final byte[] bytes;

    public Prefix(byte[] bytes) {
        this.bytes = Preconditions.checkNotNull(bytes, "bytes cannot be null").clone();
    }

    @Nonnull
    public byte[] getBytes() {
        return bytes.clone();
    }
}
