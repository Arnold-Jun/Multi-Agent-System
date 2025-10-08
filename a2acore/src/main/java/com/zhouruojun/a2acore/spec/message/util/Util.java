package com.zhouruojun.a2acore.spec.message.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.a2acore.spec.error.ContentTypeNotSupportedError;
import com.zhouruojun.a2acore.spec.error.UnsupportedOperationError;
import com.zhouruojun.a2acore.spec.message.JsonRpcResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Util {
    public static boolean isEmpty(CharSequence c) {
        return c == null || c.length() == 0;
    }

    public static boolean isEmpty(Collection c) {
        return c == null || c.isEmpty();
    }


    public static boolean areModalitiesCompatible(List<String> serverOutputModes, List<String> clientOutputModes) {
        if (serverOutputModes == null || serverOutputModes.isEmpty()) {
            return true;
        }
        if (clientOutputModes == null || clientOutputModes.isEmpty()) {
            return true;
        }
        return new ArrayList<>(clientOutputModes).retainAll(serverOutputModes);
    }

    public static <T> JsonRpcResponse<T> newIncompatibleTypesError(String requestId) {
        return new JsonRpcResponse<>(requestId, new ContentTypeNotSupportedError());
    }

    public static <T> JsonRpcResponse<T> newNotImplementedError(String requestId) {
        return new JsonRpcResponse<>(requestId, new UnsupportedOperationError());
    }


    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> T deepCopyJson(T object, Class<T> tClass) {
        try {
            String json = objectMapper.writeValueAsString(object);
            return objectMapper.readValue(json, tClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("object json exception: {}", e.getMessage());
            return "";
        }
    }

    public static <T> T fromJson(String json, Class<T> tClass) {
        try {
            return objectMapper.readValue(json, tClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String json = "{\"jsonrpc\":\"2.0\",\"id\":\"4cfc6c25a616401ba5095e72475181c2\",\"method\":\"tasks/sendSubscribe\",\"params\":{\"id\":\"e6d4a0a98055472bb3b6184bfc16fdfb\",\"sessionId\":\"15bd8990773f45d58a88a3c1b5865043\",\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"type\":\"text\",\"metadata\":null,\"text\":\"100����������ܻ������Ԫ\"}],\"metadata\":null},\"acceptedOutputModes\":null,\"pushNotification\":null,\"historyLength\":3,\"metadata\":null}}\n";

    public static void main(String[] args) {
        JsonRpcResponse jsonRpcResponse = Util.fromJson(json, JsonRpcResponse.class);
        System.out.println("jsonRpcResponse = " + jsonRpcResponse);
    }
}


