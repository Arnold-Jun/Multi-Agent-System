package com.zhouruojun.dataanalysisagent.agent.serializers;

import com.zhouruojun.dataanalysisagent.agent.state.AgentMessageState;
import org.bsc.langgraph4j.serializer.StateSerializer;


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
