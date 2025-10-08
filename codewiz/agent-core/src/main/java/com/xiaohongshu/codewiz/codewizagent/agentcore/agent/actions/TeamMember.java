package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions;

import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import lombok.Data;

/**
 * <p>
 * TeamMember
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/19 21:21
 */
@Data
public class TeamMember {

    private String memberName;
    private SupervisorTaskSendParams taskSendParams;

}
