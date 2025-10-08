package com.xiaohongshu.codewiz.codewizagent.agentcore.session;

import com.xiaohongshu.codewiz.codewizagent.agentcore.session.ObserverMessageState;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/9 21:05
 */
@Data
public class ObserverMessage {
    private String node;
    private ObserverMessageState state;
    private Boolean subGraph;

}
