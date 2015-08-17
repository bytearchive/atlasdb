package com.palantir.server;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.palantir.common.base.Throwables;
import com.palantir.common.supplier.AbstractWritableServiceContext;
import com.palantir.common.supplier.RemoteContextHolder;
import com.palantir.common.supplier.RemoteContextHolder.RemoteContextType;

public class InboxPopulatingContainerRequestFilter implements ContainerRequestFilter {
    ObjectMapper mapper = new ObjectMapper();
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        AbstractWritableServiceContext<Map<RemoteContextType<?>, Object>> holderContext = (AbstractWritableServiceContext<Map<RemoteContextType<?>, Object>>) RemoteContextHolder.INBOX.getHolderContext();
        if (!requestContext.getHeaders().containsKey("SERVICE_CONEXT_OUT_OF_BAND")) {
            holderContext.set(null);
            return;
        }
        Map<RemoteContextType<?>, Object> map = Maps.newHashMap();
        String header = requestContext.getHeaders().get("SERVICE_CONEXT_OUT_OF_BAND").iterator().next();
        JsonNode node = mapper.readTree(header);
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String key = it.next();
            int lastIndex = key.lastIndexOf(".");
            String enumName = key.substring(lastIndex + 1, key.length());
            String className = key.substring(0, lastIndex);
            try {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Class<Enum> clazz = (Class<Enum>) Class.forName(className);
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Enum enumValue = Enum.valueOf(clazz, enumName);
                RemoteContextType<?> remoteType = (RemoteContextType<?>) enumValue;
                Object value = mapper.treeToValue(node.get(key), remoteType.getValueType());
                map.put(remoteType, value);
            } catch (ClassNotFoundException e) {
                throw Throwables.throwUncheckedException(e);
            }
        }
        holderContext.set(map);
    }
}