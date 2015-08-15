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
package com.palantir.atlas.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.base.Preconditions;
import com.palantir.atlas.api.TableRowSelection;
import com.palantir.atlas.impl.TableMetadataCache;
import com.palantir.atlasdb.table.description.TableMetadata;

public class TableRowSelectionSerializer extends StdSerializer<TableRowSelection> {
    private static final long serialVersionUID = 1L;
    private final TableMetadataCache metadataCache;

    public TableRowSelectionSerializer(TableMetadataCache metadataCache) {
        super(TableRowSelection.class);
        this.metadataCache = metadataCache;
    }

    @Override
    public void serialize(TableRowSelection value,
                          JsonGenerator jgen,
                          SerializerProvider provider) throws IOException, JsonGenerationException {
        TableMetadata metadata = metadataCache.getMetadata(value.getTableName());
        Preconditions.checkNotNull(metadata, "Unknown table %s", value.getTableName());
        jgen.writeStartObject(); {
            jgen.writeStringField("table", value.getTableName());
            jgen.writeArrayFieldStart("rows"); {
                for (byte[] row : value.getRows()) {
                    AtlasSerializers.serializeRowish(jgen, metadata.getRowMetadata(), row);
                }
            } jgen.writeEndArray();
            if (!value.getColumnSelection().allColumnsSelected()) {
                jgen.writeArrayFieldStart("cols"); {
                    for (byte[] col : value.getColumnSelection().getSelectedColumns()) {
                        jgen.writeUTF8String(col, 0, col.length);
                    }
                } jgen.writeEndArray();
            }
        } jgen.writeEndObject();
    }
}