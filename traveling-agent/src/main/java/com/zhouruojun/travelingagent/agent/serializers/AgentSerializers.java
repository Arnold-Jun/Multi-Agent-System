package com.zhouruojun.travelingagent.agent.serializers;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import org.bsc.langgraph4j.serializer.StateSerializer;

/**
 * 智能体状态序列化器枚举
 * 提供统一的状态序列化器访问接口
 */
public enum AgentSerializers {

    MAIN_GRAPH(new MainGraphStateSerializer()),
    SUBGRAPH(new SubgraphStateSerializer());

    private final StateSerializer<?> serializer;

    AgentSerializers(StateSerializer<?> serializer) {
        this.serializer = serializer;
    }

    /**
     * 获取主图状态序列化器
     */
    @SuppressWarnings("unchecked")
    public StateSerializer<MainGraphState> mainGraph() {
        return (StateSerializer<MainGraphState>) this.serializer;
    }

    /**
     * 获取子图状态序列化器
     */
    @SuppressWarnings("unchecked")
    public StateSerializer<SubgraphState> subgraph() {
        return (StateSerializer<SubgraphState>) this.serializer;
    }

    /**
     * 创建主图状态序列化器
     */
    public static StateSerializer<MainGraphState> createMainGraphStateSerializer() {
        return new MainGraphStateSerializer();
    }

    /**
     * 创建子图状态序列化器
     */
    public static StateSerializer<SubgraphState> createSubgraphStateSerializer() {
        return new SubgraphStateSerializer();
    }

}


