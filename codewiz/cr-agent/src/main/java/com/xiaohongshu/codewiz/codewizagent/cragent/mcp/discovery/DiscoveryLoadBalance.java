package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.discovery;

import com.xiaohongshu.infra.xds.eds.Instance;
import java.util.List;

/**
 * <p>
 * 负载均衡接口
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/23 11:50
 */
public interface DiscoveryLoadBalance<T> {

    /**
     * 负载均衡算法
     *
     * @param serviceName 服务名称
     * @param instances   实例列表
     * @return 选中的实例
     */
    T next(String serviceName, List<T> instances);
}
