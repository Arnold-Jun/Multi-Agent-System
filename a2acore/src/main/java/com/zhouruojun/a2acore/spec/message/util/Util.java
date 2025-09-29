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

//    static {
//        //������ԣ������˽������Ƿ��Զ��ر���Щ������parser�Լ�������Դ??//// �����ֹ�������Ӧ�ò��ò��ֱ�ȥ�ر���Щ����������parser�Ļ���������InputStream��reader??////Ĭ����true
//        objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
////�Ƿ��������ʹ��Java/C++ ��ʽ��ע�ͣ�����'/'+'*' ??//' ����??//        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
//
//
////�Ƿ�������������ס�������ƺ��ַ���??//        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
//
////�Ƿ�����JSON�ַ������������ſ����ַ���ֵС??2��ASCII�ַ��������Ʊ���ͻ��з�??//        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
//
////�Ƿ�����JSON�����Զ�??��??//        objectMapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
//
////null�����Բ�����??//        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//
////����ĸ˳��������??Ĭ��false
//        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
//
////�Ƿ������������,Ĭ��false
//        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
//
////���л�Date����ʱ��timestamps�����Ĭ��true
//        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
//
////���л�ö���Ƿ���toString()�������Ĭ��false����Ĭ����name()����??//        objectMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
//
////���л�ö���Ƿ���ordinal()�������Ĭ��false
//        objectMapper.configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, false);
//
////���л���Ԫ������ʱ���������������Ĭ��false
//        objectMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
//
////���л�Mapʱ��key�������������Ĭ��false
//        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
//
////���л�char[]ʱ��json���������Ĭ��false
//        objectMapper.configure(SerializationFeature.WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS, true);
//

    /// /���л�BigDecimalʱ�����ԭʼ���ֻ��ǿ�ѧ������Ĭ��false������toPlainString()��ѧ������ʽ����??//        objectMapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);
//    }
//
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


