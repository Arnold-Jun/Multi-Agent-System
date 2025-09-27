package com.zhouruojun.agentcore.agent.serializers;

import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import org.bsc.langgraph4j.serializer.StateSerializer;

public enum AgentSerializers {


    STD(new STDStateSerializer()),
    JSON(new JSONStateSerializer());

    private final StateSerializer<AgentMessageState> serializer;

    AgentSerializers(StateSerializer<AgentMessageState> serializer) {
        this.serializer = serializer;
    }

    public StateSerializer<AgentMessageState> object() {
        return this.serializer;
    }

}
