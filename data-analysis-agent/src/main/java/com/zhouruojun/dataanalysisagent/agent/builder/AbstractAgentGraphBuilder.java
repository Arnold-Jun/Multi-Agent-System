package com.zhouruojun.dataanalysisagent.agent.builder;

import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.CallAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.ExecuteTools;
import com.zhouruojun.dataanalysisagent.agent.serializers.AgentSerializers;
import com.zhouruojun.dataanalysisagent.agent.state.AgentMessageState;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 抽象智能体图构建器基类
 * 使用模板方法模式消除重复代码
 */
public abstract class AbstractAgentGraphBuilder {
    protected StreamingChatLanguageModel streamingChatLanguageModel;
    protected ChatLanguageModel chatLanguageModel;
    protected StateSerializer<AgentMessageState> stateSerializer;
    protected List<ToolSpecification> tools;
    protected BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue;
    protected DataAnalysisToolCollection mainToolCollection;

    /**
     * 设置聊天语言模型
     */
    public AbstractAgentGraphBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * 设置流式聊天语言模型
     */
    public AbstractAgentGraphBuilder streamingChatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * 设置状态序列化器
     */
    public AbstractAgentGraphBuilder stateSerializer(StateSerializer<AgentMessageState> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    /**
     * 设置工具列表
     */
    public AbstractAgentGraphBuilder tools(List<ToolSpecification> tools) {
        this.tools = tools;
        return this;
    }
    
    /**
     * 设置主工具集合
     */
    public AbstractAgentGraphBuilder mainToolCollection(DataAnalysisToolCollection mainToolCollection) {
        this.mainToolCollection = mainToolCollection;
        return this;
    }

    /**
     * 模板方法：构建状态图
     */
    public final StateGraph<AgentMessageState> build() throws GraphStateException {
        // 1. 参数验证
        validateParameters();
        
        // 2. 初始化默认值
        initializeDefaults();
        
        // 3. 创建智能体
        BaseAgent agent = createAgent();
        
        // 4. 创建节点
        CallAgent callAgent = createCallAgent(agent);
        ExecuteTools executeTools = createExecuteTools(agent);
        
        // 5. 构建图 - 子类实现
        return buildGraph(callAgent, executeTools);
    }

    /**
     * 参数验证
     */
    protected void validateParameters() {
        if (streamingChatLanguageModel != null && chatLanguageModel != null) {
            throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
        }
        if (streamingChatLanguageModel == null && chatLanguageModel == null) {
            throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
        }

    }

    /**
     * 初始化默认值
     */
    protected void initializeDefaults() {
        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.STD.object();
        }
        
        // 创建队列用于流式输出
        queue = new LinkedBlockingQueue<>();
    }

    /**
     * 创建智能体 - 子类实现
     */
    protected BaseAgent createAgent() {
        // 如果使用mainToolCollection，则从工具集合获取工具
        if (mainToolCollection != null) {
            return createAgentWithToolCollection(mainToolCollection);
        }
        
        
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(tools)
                .agentName(getAgentName())
                .prompt(getPrompt())
                .build();
    }

    /**
     * 创建智能体 - 使用工具集合的统一方式
     */
    protected BaseAgent createAgentWithToolCollection(DataAnalysisToolCollection toolCollection) {
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolCollection.getToolsByAgentName(getAgentName()))
                .agentName(getAgentName())
                .prompt(getPrompt())
                .build();
    }

    /**
     * 创建调用智能体节点
     */
    protected CallAgent createCallAgent(BaseAgent agent) {
        CallAgent callAgent = new CallAgent(getAgentName(), agent);
        callAgent.setQueue(queue);
        return callAgent;
    }

    /**
     * 创建执行工具节点
     */
    protected ExecuteTools createExecuteTools(BaseAgent agent) {
        // 使用工厂方法创建轻量级的工具集合
        DataAnalysisToolCollection tempToolCollection = createTempToolCollection();
        return new ExecuteTools(getActionName(), agent, tempToolCollection);
    }

    /**
     * 创建临时工具集合 - 子类实现
     */
    protected abstract DataAnalysisToolCollection createTempToolCollection();

    /**
     * 构建图 - 子类实现
     */
    protected abstract StateGraph<AgentMessageState> buildGraph(
            CallAgent callAgent,
            ExecuteTools executeTools
    ) throws GraphStateException;


    /**
     * 获取智能体名称 - 子类实现
     */
    protected abstract String getAgentName();

    /**
     * 获取动作名称 - 子类实现
     */
    protected abstract String getActionName();

    /**
     * 获取提示词 - 子类实现
     */
    protected abstract String getPrompt();


    /**
     * 获取标准的边条件 - 辅助方法
     * 直接结束，不再调用额外的总结节点
     */
    protected EdgeAction<AgentMessageState> getStandardAgentShouldContinue() {
        return (state) -> {
            var lastMessage = state.lastMessage();
            if (lastMessage.isPresent() && lastMessage.get() instanceof dev.langchain4j.data.message.AiMessage) {
                dev.langchain4j.data.message.AiMessage aiMessage = (dev.langchain4j.data.message.AiMessage) lastMessage.get();
                if (aiMessage.hasToolExecutionRequests()) {
                    return "action";
                }
            }
            // 直接结束，不再调用额外的总结节点
            return "FINISH";
        };
    }

    /**
     * 获取标准的工具执行回调边条件 - 辅助方法
     */
    protected EdgeAction<AgentMessageState> getStandardActionShouldContinue() {
        return (state) -> "callback";
    }

}