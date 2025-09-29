package com.zhouruojun.dataanalysisagent.agent.actions;

import com.zhouruojun.dataanalysisagent.agent.parser.PlannerJsonParser;
import com.zhouruojun.dataanalysisagent.agent.state.MainGraphState;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoList;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoTask;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TodoList解析器节点
 * 专门用于解析Planner输出的JSON格式，并更新TodoList
 */
@Slf4j
public class TodoListParser implements NodeAction<MainGraphState> {
    
    // 使用静态实例，避免重复创建
    private static final PlannerJsonParser JSON_PARSER = new PlannerJsonParser();
    
    @Override
    public Map<String, Object> apply(MainGraphState state) {
        log.info("开始解析Planner的JSON输出");
        
        try {
            // 获取和验证最后一条消息
            AiMessage aiMessage = state.lastMessage()
                .filter(msg -> msg instanceof AiMessage)
                .map(msg -> (AiMessage) msg)
                .orElse(null);
                
            if (aiMessage == null) {
                log.warn("无法获取有效的AI消息进行JSON解析");
                return createErrorResult(state, "无法获取有效的AI消息");
            }

            String plannerOutput = aiMessage.text();
            
            log.info("Planner输出内容: {}", plannerOutput);
            
            // 解析JSON并更新TodoList
            TodoList updatedTodoList = JSON_PARSER.parseAndUpdateTodoList(plannerOutput, state.getTodoList().orElse(null));
            
            log.info("TodoList解析成功，当前任务数量: {}", updatedTodoList.getTasks().size());
            log.info("TodoList摘要: {}", updatedTodoList.getSummary());
            
            // 返回成功结果（会更新状态）
            Map<String, Object> result = createSuccessResult(state, updatedTodoList);
            return result;
            
        } catch (PlannerJsonParser.PlannerParseException e) {
            log.error("JSON解析失败: {}", e.getMessage(), e);
            return createErrorResult(state, "JSON解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("TodoList解析过程中发生未知错误: {}", e.getMessage(), e);
            return createErrorResult(state, "解析过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 创建成功结果
     */
    private Map<String, Object> createSuccessResult(MainGraphState state, TodoList updatedTodoList) {
        Map<String, Object> result = new HashMap<>();
        result.put("messages", state.messages());
        
        // 更新状态后再复制字段
        state.updateTodoList(updatedTodoList);
        copyCommonStateFields(state, result);
        
        return result;
    }
    
    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(MainGraphState state, String errorMessage) {
        log.error("A2A TodoList解析失败: {}", errorMessage);
        
        // 构建更详细的错误提示
        String detailedErrorMessage = buildDetailedErrorPrompt(errorMessage, state);
        
        // 将错误信息添加到消息中，让Planner重新推理
        List<ChatMessage> errorMessages = new ArrayList<>(state.messages());
        errorMessages.add(UserMessage.from(detailedErrorMessage));
        
        Map<String, Object> result = new HashMap<>();
        result.put("next", "planner"); // 路由回planner重新推理
        result.put("messages", errorMessages);
        copyCommonStateFields(state, result);
        
        return result;
    }
    
    /**
     * 复制通用的状态字段到结果中
     */
    private void copyCommonStateFields(MainGraphState state, Map<String, Object> result) {
        if (state.getTodoList().isPresent()) {
            result.put("todoList", state.getTodoList().get());
        }
        if (state.getOriginalUserQuery().isPresent()) {
            result.put("originalUserQuery", state.getOriginalUserQuery().get());
        }
        if (state.getSubgraphResults().isPresent()) {
            result.put("subgraphResults", state.getSubgraphResults().get());
        }
    }
    
    /**
     * 构建详细的错误提示
     */
    private String buildDetailedErrorPrompt(String errorMessage, MainGraphState state) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("JSON解析失败: ").append(errorMessage).append("\n\n");
        
        // 添加当前TodoList状态信息
        if (state.getTodoList().isPresent()) {
            TodoList currentTodoList = state.getTodoList().get();
            prompt.append("当前TodoList状态:\n");
            prompt.append("- 总任务数: ").append(currentTodoList.getTasks().size()).append("\n");
            if (!currentTodoList.getTasks().isEmpty()) {
                prompt.append("- 可用任务ID: ");
                List<String> allIds = currentTodoList.getTasks().stream()
                    .map(task -> task.getUniqueId() != null ? task.getUniqueId() : task.getTaskId())
                    .collect(Collectors.toList());
                prompt.append("\"").append(String.join("\", \"", allIds)).append("\"\n");
                prompt.append("- 任务详情:\n");
                for (TodoTask task : currentTodoList.getTasks()) {
                    prompt.append("  ").append(task.getSummary()).append("\n");
                }
            } else {
                prompt.append("- 当前没有任务\n");
            }
        }
        
        prompt.append("\n请重新输出正确的JSON格式，注意：\n");
        prompt.append("1. 如果要修改任务状态，不要同时删除同一个任务\n");
        prompt.append("2. 确保要修改的任务ID在可用任务列表中\n");
        prompt.append("3. JSON格式必须正确，包含add、modify、delete字段\n");
        prompt.append("4. 如果任务不存在，请先添加任务再修改\n");
        
        return prompt.toString();
    }
}
