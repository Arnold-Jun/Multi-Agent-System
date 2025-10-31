package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashMap;
import java.util.Map;

/**
 * 表单输入节点
 * 专门处理结构化表单输入场景
 * 
 * 功能说明：
 * 1. 从preprocessor路由而来，用于收集用户的旅游规划信息
 * 2. 作为图执行的暂停点，等待用户填写表单
 * 3. 保留formInputRequired和formSchema，供前端渲染表单
 * 4. 表单提交后，通过submitForm恢复执行，路由回preprocessor继续处理
 */
@Slf4j
public class FormInput implements NodeAction<MainGraphState> {

    @Override
    public Map<String, Object> apply(MainGraphState state) throws Exception {
        log.info("FormInput executing - waiting for form submission for session: {}", state.getSessionId().orElse("unknown"));
        
        Map<String, Object> result = new HashMap<>();
        
        // 表单输入场景：保持formInputRequired和formSchema不变
        // finalResponse已经在CallPreprocessorAgent中设置
        String finalResponse = state.getFinalResponse().orElse("请填写以下信息以继续规划您的旅行：");
        result.put("finalResponse", finalResponse);
        result.put("formInputRequired", true);
        
        // 保持formSchema（如果存在）
        state.getFormSchema().ifPresent(schema -> result.put("formSchema", schema));
        
        log.info("FormInput: 已设置表单输入标志，等待用户提交表单");
        
        return result;
    }
}

