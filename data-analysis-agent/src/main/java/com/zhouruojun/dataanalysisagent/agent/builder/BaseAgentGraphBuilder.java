package com.zhouruojun.dataanalysisagent.agent.builder;

import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.CallSubAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.ExecuteTools;
import com.zhouruojun.dataanalysisagent.agent.state.BaseAgentState;
import com.zhouruojun.dataanalysisagent.agent.state.SubgraphState;
import com.zhouruojun.dataanalysisagent.config.ParallelExecutionConfig;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 抽象智能体图构建器基类
 * 使用模板方法模式消除重复代码
 */
public abstract class BaseAgentGraphBuilder<T extends BaseAgentState> {
    protected StreamingChatLanguageModel streamingChatLanguageModel;
    protected ChatLanguageModel chatLanguageModel;
    protected StateSerializer<T> stateSerializer;
    protected ParallelExecutionConfig parallelExecutionConfig;
    protected List<ToolSpecification> tools;
    protected BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue;
    protected DataAnalysisToolCollection mainToolCollection;
    
    // Checkpoint相关配置
    protected BaseCheckpointSaver checkpointSaver;
    protected boolean enableCheckpoint = true;
    protected String checkpointNamespace = "subgraph";

    /**
     * 设置聊天语言模型
     */
    public BaseAgentGraphBuilder<T> chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * 设置流式聊天语言模型
     */
    public BaseAgentGraphBuilder<T> streamingChatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * 设置状态序列化器
     */
    public BaseAgentGraphBuilder<T> stateSerializer(StateSerializer<T> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    /**
     * 设置工具列表
     */
    public BaseAgentGraphBuilder<T> tools(List<ToolSpecification> tools) {
        this.tools = tools;
        return this;
    }
    
    /**
     * 设置并行执行配置
     */
    public BaseAgentGraphBuilder<T> parallelExecutionConfig(ParallelExecutionConfig parallelExecutionConfig) {
        this.parallelExecutionConfig = parallelExecutionConfig;
        return this;
    }
    
    /**
     * 设置主工具集合
     */
    public BaseAgentGraphBuilder<T> mainToolCollection(DataAnalysisToolCollection mainToolCollection) {
        this.mainToolCollection = mainToolCollection;
        return this;
    }
    
    /**
     * 设置checkpoint保存器
     */
    public BaseAgentGraphBuilder<T> checkpointSaver(BaseCheckpointSaver checkpointSaver) {
        this.checkpointSaver = checkpointSaver;
        return this;
    }
    
    /**
     * 设置是否启用checkpoint
     */
    public BaseAgentGraphBuilder<T> enableCheckpoint(boolean enableCheckpoint) {
        this.enableCheckpoint = enableCheckpoint;
        return this;
    }
    
    /**
     * 设置checkpoint命名空间
     */
    public BaseAgentGraphBuilder<T> checkpointNamespace(String checkpointNamespace) {
        this.checkpointNamespace = checkpointNamespace;
        return this;
    }

    /**
     * 模板方法：构建状态图
     */
    public final StateGraph<T> build() throws GraphStateException {
        // 1. 参数验证
        validateParameters();
        
        // 2. 初始化默认值
        initializeDefaults();
        
        // 3. 创建智能体
        BaseAgent agent = createAgent();
        
        // 4. 创建节点
        CallSubAgent callAgent = createCallAgent(agent);
        ExecuteTools<T> executeTools = createExecuteTools(agent);
        
        // 5. 构建图 - 使用带checkpoint的构建方法
        return buildGraphWithCheckpoint(callAgent, executeTools);
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
            // 子类需要重写此方法来设置特定的序列化器
            throw new IllegalStateException("stateSerializer must be set by subclass");
        }
        
        // 创建队列用于流式输出
        queue = new LinkedBlockingQueue<>();
        
        // 初始化checkpoint保存器
        if (checkpointSaver == null && enableCheckpoint) {
            checkpointSaver = new MemorySaver();
        }
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
     * 创建智能体
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
    protected CallSubAgent createCallAgent(BaseAgent agent) {
        CallSubAgent callAgent = new CallSubAgent(getAgentName(), agent);

        @SuppressWarnings("unchecked")
        BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>> subgraphQueue =
            (BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>>) (BlockingQueue<?>) queue;
        callAgent.setQueue(subgraphQueue);
        return callAgent;
    }

    /**
     * 创建执行工具节点
     */
    protected ExecuteTools<T> createExecuteTools(BaseAgent agent) {
        DataAnalysisToolCollection tempToolCollection = createTempToolCollection();
        return new ExecuteTools<>(getActionName(), agent, tempToolCollection, parallelExecutionConfig);
    }

    /**
     * 创建临时工具集合 - 子类实现
     */
    protected abstract DataAnalysisToolCollection createTempToolCollection();

    /**
     * 构建图 - 子类实现
     */
    protected abstract StateGraph<T> buildGraph(
            CallSubAgent callAgent,
            ExecuteTools<T> executeTools
    ) throws GraphStateException;
    
    /**
     * 构建带checkpoint的图 - 子类可以重写此方法来自定义checkpoint行为
     */
    protected StateGraph<T> buildGraphWithCheckpoint(
            CallSubAgent callAgent,
            ExecuteTools<T> executeTools
    ) throws GraphStateException {
        // 直接返回构建的图，checkpoint配置在编译时处理
        return buildGraph(callAgent, executeTools);
    }


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
    protected EdgeAction<T> getStandardAgentShouldContinue() {
        return (state) -> {
            var lastMessage = state.lastMessage();
            if (lastMessage.isPresent() && lastMessage.get() instanceof dev.langchain4j.data.message.AiMessage aiMessage) {
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
    protected EdgeAction<T> getStandardActionShouldContinue() {
        return (state) -> "callback";
    }
    
    /**
     * 获取checkpoint保存器
     */
    protected BaseCheckpointSaver getCheckpointSaver() {
        return checkpointSaver;
    }
    
    /**
     * 是否启用了checkpoint
     */
    protected boolean isCheckpointEnabled() {
        return enableCheckpoint && checkpointSaver != null;
    }
    
    /**
     * 获取checkpoint命名空间
     */
    protected String getCheckpointNamespace() {
        return checkpointNamespace;
    }
    
    /**
     * 创建checkpoint配置
     */
    protected CompileConfig createCheckpointConfig() {
        if (!isCheckpointEnabled()) {
            return CompileConfig.builder().build();
        }
        
        return CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .build();
    }

}