package com.xiaohongshu.codewiz.codewizagent.agentcore.a2a;

import com.xiaohongshu.codewiz.a2acore.spec.UpdateEvent;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/15 20:33
 */
@Data
public class A2aTaskUpdate {

    private String taskId;
    private String sessionId;
    private UpdateEvent updateEvent;
    private String updateType;

}
