package com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local;

import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/24 11:12
 */
@Data
public class LocalAgentReplyMessage {

    private LocalToolMessageRequest request;
    private LocalToolMessageResponse response;

}
