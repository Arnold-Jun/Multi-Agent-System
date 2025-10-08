package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.discovery;

import com.dianping.cat.internal.shaded.com.google.api.Service;
import com.xiaohongshu.infra.xds.eds.Instance;
import io.swagger.models.auth.In;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/23 11:19
 */
public abstract class BaseLoadBalance<T> implements DiscoveryLoadBalance<T> {

    abstract T getNext(String serviceName, List<T> instances);

    @Override
    public T next(String serviceName, List<T> instances) {
        try {
            return getNext(serviceName, instances);
        } catch (Exception e) {
            return instances.getFirst();
        }
    }
}
