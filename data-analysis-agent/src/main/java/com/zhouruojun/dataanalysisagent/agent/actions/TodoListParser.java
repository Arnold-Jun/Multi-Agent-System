package com.zhouruojun.dataanalysisagent.agent.actions;

import com.zhouruojun.dataanalysisagent.agent.parser.PlannerJsonParser;
import com.zhouruojun.dataanalysisagent.agent.state.MainGraphState;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoList;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.*;

/**
 * TodoList解析器节点
 * 专门用于解析Planner输出的JSON格式，并更新TodoList
 */
@Slf4j
public class TodoListParser implements NodeAction<MainGraphState> {
    
    private final PlannerJsonParser jsonParser;
    
    public TodoListParser() {
        this.jsonParser = new PlannerJsonParser();
    }
    
    @Override
    public Map<String, Object> apply(MainGraphState state) {
        log.info("开始解析Planner的JSON输出");
        
        try {
            // 获取最后一条消息
            Optional<ChatMessage> lastMessageOpt = state.lastMessage();
            if (lastMessageOpt.isEmpty()) {
                log.warn("没有最后一条消息，无法解析JSON");
                return createErrorResult(state, "没有最后一条消息");
            }
            
            ChatMessage lastMessage = lastMessageOpt.get();
            if (!(lastMessage instanceof AiMessage aiMessage)) {
                log.warn("最后一条消息不是AiMessage，无法解析JSON");
                return createErrorResult(state, "最后一条消息不是AI消息");
            }

            String plannerOutput = aiMessage.text();
            
            log.info("Planner输出内容: {}", plannerOutput);
            
            // 解析JSON并更新TodoList
            TodoList updatedTodoList = jsonParser.parseAndUpdateTodoList(plannerOutput, state.getTodoList().orElse(null));
            
            // 更新状态
            state.updateTodoList(updatedTodoList);
            
            log.info("TodoList解析成功，当前任务数量: {}", updatedTodoList.getTasks().size());
            log.info("TodoList摘要: {}", updatedTodoList.getSummary());
            
            // 返回结果，让路由逻辑决定下一步
            Map<String, Object> result = new HashMap<>();
            result.put("messages", state.messages());
            result.put("todoList", updatedTodoList);
            
            // 保持其他状态字段
            if (state.getOriginalUserQuery().isPresent()) {
                result.put("originalUserQuery", state.getOriginalUserQuery().get());
            }
            if (state.getSubgraphResults().isPresent()) {
                result.put("subgraphResults", state.getSubgraphResults().get());
            }
            
            return result;
            
        } catch (PlannerJsonParser.PlannerParseException e) {
            log.error("TodoList JSON解析失败: {}", e.getMessage(), e);
            return createErrorResult(state, "JSON解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("TodoList解析过程中发生未知错误: {}", e.getMessage(), e);
            return createErrorResult(state, "解析过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(MainGraphState state, String errorMessage) {
        log.error("TodoList解析失败: {}", errorMessage);
        
        // 将错误信息添加到消息中，让Planner重新推理
        List<ChatMessage> errorMessages = new ArrayList<>(state.messages());
        errorMessages.add(UserMessage.from("JSON解析失败: " + errorMessage + "\n请重新输出正确的JSON格式"));
        
        Map<String, Object> result = new HashMap<>();
        result.put("next", "planner"); // 路由回planner重新推理
        result.put("messages", errorMessages);
        
        // 保持其他状态字段
        if (state.getTodoList().isPresent()) {
            result.put("todoList", state.getTodoList().get());
        }
        if (state.getOriginalUserQuery().isPresent()) {
            result.put("originalUserQuery", state.getOriginalUserQuery().get());
        }
        if (state.getSubgraphResults().isPresent()) {
            result.put("subgraphResults", state.getSubgraphResults().get());
        }
        
        return result;
    }
}
