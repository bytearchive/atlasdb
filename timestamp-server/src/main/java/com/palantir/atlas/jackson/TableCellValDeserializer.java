package com.palantir.atlas.jackson;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.palantir.atlas.api.TableCellVal;
import com.palantir.atlas.impl.TableMetadataCache;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.table.description.TableMetadata;

public class TableCellValDeserializer extends StdDeserializer<TableCellVal> {
    private static final long serialVersionUID = 1L;
    private final TableMetadataCache metadataCache;

    protected TableCellValDeserializer(TableMetadataCache metadataCache) {
        super(TableCellVal.class);
        this.metadataCache = metadataCache;
    }

    @Override
    public TableCellVal deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = jp.readValueAsTree();
        String tableName = node.get("table").textValue();
        TableMetadata metadata = metadataCache.getMetadata(tableName);
        Map<Cell, byte[]> values = AtlasDeserializers.deserializeCellVals(metadata, node.get("data"));
        return new TableCellVal(tableName, values);
    }
}