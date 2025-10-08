package com.xiaohongshu.codewiz.codewizagent.cragent.agent.serializers;

import com.xiaohongshu.codewiz.codewizagent.cragent.agent.serializers.JSONStateSerializer;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.serializers.STDStateSerializer;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import org.bsc.langgraph4j.serializer.StateSerializer;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 20:01
 */
public enum AgentSerializers {


    STD(new STDStateSerializer()),
    JSON(new JSONStateSerializer());

    private final StateSerializer<AgentMessageState> serializer;

    private AgentSerializers(StateSerializer<AgentMessageState> serializer) {
        this.serializer = serializer;
    }

    public StateSerializer<AgentMessageState> object() {
        return this.serializer;
    }

}
