package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.discovery;

import com.xiaohongshu.infra.xds.eds.Instance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/23 11:17
 */
public class RoundLoadBalance<T> extends BaseLoadBalance<T> {

    private Map<String, Integer> serviceRandomMap = new HashMap<>();

    @Override
    T getNext(String serviceName, List<T> instances) {
        Integer index = serviceRandomMap.getOrDefault(serviceName, 0);
        if (index >= instances.size()) {
            index = 0;
        }
        T instance = instances.get(index);
        serviceRandomMap.put(serviceName, index + 1);
        return instance;
    }
}
