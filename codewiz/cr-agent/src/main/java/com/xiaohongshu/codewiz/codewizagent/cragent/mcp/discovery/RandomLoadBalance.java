package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.discovery;

import com.xiaohongshu.infra.xds.eds.Instance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomUtils;

/**
 * <p>
 * 简单的随机负载均衡算法
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/23 11:13
 */
public class RandomLoadBalance<T> extends BaseLoadBalance<T> {

    @Override
    T getNext(String serviceName, List<T> instances) {
        Integer index = RandomUtils.nextInt(0, instances.size());
        return instances.get(index);
    }
}
