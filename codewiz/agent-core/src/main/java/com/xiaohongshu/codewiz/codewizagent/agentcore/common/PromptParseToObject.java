package com.xiaohongshu.codewiz.codewizagent.agentcore.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 从Prompt中解析出对象
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/19 23:46
 */
@Slf4j
public class PromptParseToObject {

    private static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * "\n\n<TeamMember>\n<memberName>invokeAgent</memberName>\n<taskSendParams>\n{\"invokeAgent\": \"CrAgent\", \n\"message\": {\"content\": \"帮我发起CR：\\n1.获取当前仓库信息。\\n2.检查当前分支工作区是否有未提交改动，如有未提交改动，尝试提交改动到远端\\n3.获取推荐的目标分支，并检查当前仓库、源分支、目标分支的权限\\n4.检查当前源分支关联的需求\\n5.获取推荐审批人信息。\\n6.检查当前是否存在代码冲突\\n7.获取cr信息模版\\n8.创建云效CR\"},\n\"skills\": [\"create_cr\"]}\n</taskSendParams>\n</TeamMember>"
     */
    public static <T> T parse(String prompt, Class<T> clazz) {
        try {
            // 创建一个Map来存储解析结果
            Map<String, Object> resultMap = new HashMap<>();
            
            // 解析TeamMember标签
            String teamMemberPattern = "<TeamMember>(.*?)</TeamMember>";
            Pattern pattern = Pattern.compile(teamMemberPattern, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(prompt);
            
            if (matcher.find()) {
                String teamMemberContent = matcher.group(1).trim();
                
                // 解析MemberName标签
                String memberNamePattern = "<memberName>(.*?)</memberName>";
                Pattern memberNamePatternCompiled = Pattern.compile(memberNamePattern);
                Matcher memberNameMatcher = memberNamePatternCompiled.matcher(teamMemberContent);
                
                if (memberNameMatcher.find()) {
                    resultMap.put("memberName", memberNameMatcher.group(1).trim());
                }
                
                // 解析TaskSendParams标签
                String taskSendParamsPattern = "<taskSendParams>(.*?)</taskSendParams>";
                Pattern taskSendParamsPatternCompiled = Pattern.compile(taskSendParamsPattern, Pattern.DOTALL);
                Matcher taskSendParamsMatcher = taskSendParamsPatternCompiled.matcher(teamMemberContent);
                
                if (taskSendParamsMatcher.find()) {
                    String jsonStr = taskSendParamsMatcher.group(1).trim();
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> taskSendParamsMap = objectMapper.readValue(jsonStr, 
                            new TypeReference<Map<String, Object>>() {});
                    resultMap.put("taskSendParams", taskSendParamsMap);
                }
            }
            
            // 将整个Map转换为目标类型
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.convertValue(resultMap, clazz);
        } catch (Exception e) {
            throw new RuntimeException("解析提示词失败: " + e.getMessage(), e);
        }
    }
}
