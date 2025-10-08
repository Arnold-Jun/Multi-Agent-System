package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/19 21:26
 */
@Data
public class SupervisorTaskSendParams {

    private String agentInvoke;
    private Map<String, Object> message;
    private List<String> skills;

}
