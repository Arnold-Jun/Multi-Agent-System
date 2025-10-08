package com.xiaohongshu.codewiz.codewizagent.agentcore.session.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.UserPrincipal;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JsonRpcMethodRegistry {
    // 方法路由表：Map<方法名, MethodInvoker>
    private final Map<String, MethodInvoker> methodRegistry = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void registerService(Object service) {
        Class<?> clazz = service.getClass();

        // 遍历实现类的所有方法
        for (Method method : clazz.getDeclaredMethods()) {
            // 尝试获取当前方法上的注解
            JsonRpcMethod methodAnnotation = method.getAnnotation(JsonRpcMethod.class);

            // 如果方法本身有注解，直接注册（优先级最高）
            if (methodAnnotation != null) {
                registerMethod(service, method, methodAnnotation.value());
                continue;
            }

            // 查找接口方法上的注解
            String rpcMethodName = findInterfaceMethodAnnotation(method);
            if (rpcMethodName != null) {
                registerMethod(service, method, rpcMethodName);
            }
        }
    }

    /**
     * 在接口中查找方法上的 @JsonRpcMethod 注解
     */
    private String findInterfaceMethodAnnotation(Method implMethod) {
        // 获取实现类的所有接口（包括父接口）
        Set<Class<?>> interfaces = getAllInterfaces(implMethod.getDeclaringClass());

        for (Class<?> interfaceClazz : interfaces) {
            try {
                // 获取接口中的对应方法
                Method interfaceMethod = interfaceClazz.getMethod(
                        implMethod.getName(),
                        implMethod.getParameterTypes()
                );

                // 检查接口方法上的注解
                JsonRpcMethod annotation = interfaceMethod.getAnnotation(JsonRpcMethod.class);
                if (annotation != null) {
                    return annotation.value();
                }
            } catch (NoSuchMethodException e) {
                // 接口中没有该方法，继续查找其他接口
            }
        }
        return null;
    }

    /**
     * 递归获取类实现的所有接口（包括父接口）
     */
    private Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new HashSet<>();
        while (clazz != null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                interfaces.add(iface);
                interfaces.addAll(getAllInterfaces(iface)); // 递归父接口
            }
            clazz = clazz.getSuperclass(); // 检查父类实现的接口
        }
        return interfaces;
    }

    /**
     * 注册方法到路由表
     */
    private void registerMethod(Object service, Method method, String rpcMethodName) {
        methodRegistry.put(rpcMethodName, new MethodInvoker(service, method));
    }

    /**
     * 调用 JSON-RPC 方法
     * @param methodName 方法名（对应注解的 value）
     * @param params     JSON-RPC 的 params 参数（Map 或 List）
     * @return 方法执行结果
     */
    public Object invokeMethod(String methodName, Object params) throws Exception {
        MethodInvoker invoker = methodRegistry.get(methodName);
        if (invoker == null) {
            return JsonRpcError.methodNotFound();
        }
        return invoker.invoke(params);
    }

    /**
     * 调用 JSON-RPC 方法
     * @param methodName 方法名（对应注解的 value）
     * @param params     JSON-RPC 的 params 参数（Map 或 List）
     * @return 方法执行结果
     */
    public Object invokeMethodWithUser(String methodName, Object params, UserPrincipal user) throws Exception {
        MethodInvoker invoker = methodRegistry.get(methodName);
        if (invoker == null) {
            return JsonRpcError.methodNotFound();
        }
        return invoker.invoke(params, user);
    }

    /**
     * 内部类：封装方法反射调用逻辑
     */
    private class MethodInvoker {
        private final Object target;      // 方法所属对象实例
        private final Method method;      // 方法对象
        private final Class<?>[] paramTypes; // 方法参数类型

        public MethodInvoker(Object target, Method method) {
            this.target = target;
            this.method = method;
            this.paramTypes = method.getParameterTypes();
            method.setAccessible(true); // 允许调用私有方法
        }

        public Object invoke(Object params) throws Exception {
            // 将 params 转换为方法参数类型
            Object[] args = parseParams(params, null);
            return method.invoke(target, args);
        }

        public Object invoke(Object params, UserPrincipal user) throws Exception {
            // 将 params 转换为方法参数类型
            Object[] args = parseParams(params, user);
            return method.invoke(target, args);
        }

        /**
         * 解析 JSON-RPC params 为方法参数
         */
        private Object[] parseParams(Object params, UserPrincipal user) throws Exception {
            if (paramTypes.length == 0) {
                return new Object[0]; // 无参方法
            }

            // 单参数：将 params 整体转换为目标类型
            if (paramTypes.length == 1) {
                if (user != null && paramTypes[0] == String.class) {
                    return new Object[]{user.getName()};
                }
                Object arg = objectMapper.convertValue(params, paramTypes[0]);
                return new Object[]{arg};
            }

            if (paramTypes.length == 2) {
                if (user != null && paramTypes[1] == String.class) {
                    Object arg = objectMapper.convertValue(params, paramTypes[0]);
                    return new Object[]{arg, user.getName()};
                }
            }
            // 多参数：params 必须是数组（即 List）
            if (params instanceof List) {
                List<?> paramList = (List<?>) params;
                if (paramList.size() < paramTypes.length - 1) {
                    throw new IllegalArgumentException(
                            "参数数量不匹配，预期 " + paramTypes.length + " 个，实际收到 " + paramList.size()
                    );
                }

                if (paramList.size() == paramTypes.length - 1 && user != null) {
                    Object[] args = new Object[paramTypes.length + 1];
                    for (int i = 0; i < paramTypes.length; i++) {
                        args[i] = objectMapper.convertValue(paramList.get(i), paramTypes[i]);
                    }
                    args[paramTypes.length] = user.getName();
                    return args;
                }

                if (paramList.size() == paramTypes.length && user == null) {
                    Object[] args = new Object[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        args[i] = objectMapper.convertValue(paramList.get(i), paramTypes[i]);
                    }
                    return args;
                }
            }
            throw new IllegalArgumentException("Params type mismatch for method: " + method.getName());
        }
    }
}