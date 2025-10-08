# UserInput节点使用Context更新总结

## 🎯 核心改进

根据您的正确建议，**UserInput节点现在输出Scheduler的context（执行上下文）**，而不是简单的默认消息。

---

## 📊 Scheduler输出结构

Scheduler在JSON输出中包含两个关键字段：

```json
{
  "taskUpdate": {
    "taskId": "task_001",
    "status": "in_progress",
    "reason": "开始执行任务"
  },
  "nextAction": {
    "next": "userInput",
    "taskDescription": "任务的概要描述",
    "context": "详细的执行指令和上下文信息"
  }
}
```

### 两个字段的区别：

| 字段 | 作用 | 详细程度 | 适用场景 |
|------|------|----------|----------|
| **taskDescription** | 任务概要 | 简短 | 给子图看的任务标题 |
| **context** | 执行上下文 | 详细 | 包含具体指令、参数、要求 |

---

## ✅ 选择Context的原因

经过仔细思考，我选择使用**context**作为UserInput的输出，原因如下：

### 1. **Context更具体明确**
- Context包含Scheduler生成的详细执行指令
- 明确说明需要用户提供什么信息
- 包含为什么需要这个信息的上下文

### 2. **TaskDescription过于简略**
- 只是任务的概要描述
- 缺少具体的指令细节
- 更适合给子图看，而不是给用户看

### 3. **与Scheduler提示词对齐**
根据Scheduler的提示词，context的定义是：
```
"context": "子图执行所需的完整上下文信息"
```

### 4. **用户需要详细信息**
- 用户需要知道为什么要提供信息
- 用户需要知道要提供什么格式的信息
- Context可以包含示例和具体要求

---

## 🔧 实现的修改

### 1. MainGraphState添加访问方法 ✅

**文件**: `job-search-agent/src/main/java/com/zhouruojun/jobsearchagent/agent/state/MainGraphState.java`

```java
/**
 * 获取子图执行上下文（由Scheduler设置）
 * context包含详细的执行指令和要求
 */
public Optional<String> getSubgraphContext() {
    return Optional.ofNullable((String) state.get("subgraphContext"));
}

/**
 * 获取子图任务描述（由Scheduler设置）
 * taskDescription是任务的概要描述
 */
public Optional<String> getSubgraphTaskDescription() {
    return Optional.ofNullable((String) state.get("subgraphTaskDescription"));
}
```

### 2. UserInput节点读取Context ✅

**文件**: `job-search-agent/src/main/java/com/zhouruojun/jobsearchagent/agent/actions/UserInput.java`

```java
@Override
public Map<String, Object> apply(MainGraphState state) throws Exception {
    log.info("JobSearchAgent UserInput node triggered");
    
    // 优先使用Scheduler设置的context（详细的执行指令）
    // 如果没有context，则使用taskDescription
    // 如果都没有，则使用默认提示
    String userInputPrompt = state.getSubgraphContext()
            .or(() -> state.getSubgraphTaskDescription())
            .orElse("等待用户输入");
    
    log.info("UserInput prompt: {}", userInputPrompt);
    
    // 返回AiMessage，包含需要用户输入的提示信息
    return Map.of(
            "next", "", 
            "agent_response", "",
            "messages", AiMessage.aiMessage(userInputPrompt)
    );
}
```

**优先级**：
1. **第一优先**: `subgraphContext` - 详细的执行上下文
2. **第二优先**: `subgraphTaskDescription` - 任务概要
3. **最后兜底**: "等待用户输入" - 默认消息

### 3. SchedulerResponseParser添加userInput验证 ✅

**文件**: `job-search-agent/src/main/java/com/zhouruojun/jobsearchagent/agent/parser/SchedulerResponseParser.java`

```java
// 支持的下一个节点
private static final List<String> VALID_NEXT_NODES = Arrays.asList(
    "job_info_collection_subgraph",
    "resume_analysis_optimization_subgraph",
    "job_search_execution_subgraph",
    "userInput",  // ✅ 新增
    "planner",
    "summary"
);
```

---

## 🔄 完整的数据流

### 场景：Scheduler决定需要用户输入简历文件

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Scheduler分析任务                                             │
│    → 判断：需要用户上传简历文件才能继续                          │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. Scheduler生成JSON输出                                         │
│    {                                                             │
│      "nextAction": {                                             │
│        "next": "userInput",                                      │
│        "taskDescription": "简历分析任务",                        │
│        "context": "请上传您的简历文件（支持PDF、Word格式），     │
│                    文件大小不超过5MB。我们需要分析您的工作       │
│                    经历、技能和教育背景，以便为您匹配合适的      │
│                    职位。"                                       │
│      }                                                           │
│    }                                                             │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. CallSchedulerAgent.processTaskUpdate                         │
│    → result.put("subgraphContext", nextAction.getContext())     │
│    → result.put("subgraphTaskDescription",                      │
│                  nextAction.getTaskDescription())                │
│    → result.put("next", "userInput")                            │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. 图路由到UserInput节点                                         │
│    → scheduler → userInput                                       │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ 5. UserInput.apply(state)                                        │
│    → String prompt = state.getSubgraphContext()                 │
│    → prompt = "请上传您的简历文件（支持PDF、Word格式）......"   │
│    → return AiMessage.aiMessage(prompt)                          │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ 6. JobSearchTaskManager发送INPUT_REQUIRED事件                   │
│    → sendTextEvent(requestId, prompt, "INPUT_REQUIRED")         │
│    → Agent-Core收到详细的用户输入提示                            │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ 7. 用户看到详细提示并提供信息                                    │
│    → 用户看到: "请上传您的简历文件（支持PDF、Word格式）......"  │
│    → 用户上传简历文件                                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📝 Context vs TaskDescription 示例对比

### 场景1：需要用户上传简历

| 字段 | 内容示例 |
|------|----------|
| **taskDescription** | "简历分析任务" |
| **context** | "请上传您的简历文件（支持PDF、Word、TXT格式），文件大小不超过5MB。我们需要分析您的工作经历、技能和教育背景，以便为您匹配合适的职位并生成优化建议。" |

**UserInput输出**: context（详细说明）✅

### 场景2：需要用户确认薪资期望

| 字段 | 内容示例 |
|------|----------|
| **taskDescription** | "岗位搜索任务" |
| **context** | "根据您的背景，我们找到了多个合适的职位。为了精准筛选，请告诉我们您的薪资期望范围。例如：15K-25K，或者20K以上。这将帮助我们过滤掉不符合您预期的职位。" |

**UserInput输出**: context（详细说明+示例）✅

### 场景3：需要用户澄清工作地点

| 字段 | 内容示例 |
|------|----------|
| **taskDescription** | "岗位信息收集任务" |
| **context** | "您提到想在'一线城市'工作。请具体指定您期望的城市，可以选择多个：北京、上海、广州、深圳。这将帮助我们搜索到更精准的职位信息。" |

**UserInput输出**: context（详细说明+选项）✅

---

## 🎯 优势总结

### 使用Context的优势：

1. ✅ **用户体验更好** - 用户能看到详细的指引，知道要提供什么
2. ✅ **减少沟通成本** - 一次性说清楚所有要求，避免多轮交互
3. ✅ **提高准确性** - 详细的说明能让用户提供更符合要求的信息
4. ✅ **包含示例** - Context可以包含格式示例，用户更容易理解
5. ✅ **与Scheduler职责对齐** - Scheduler本就负责生成详细的执行上下文

### 如果使用TaskDescription的劣势：

1. ❌ **信息太简略** - 用户不知道具体要提供什么
2. ❌ **可能造成误解** - 缺少详细说明和示例
3. ❌ **需要多轮交互** - 用户可能提供不符合要求的信息，需要重新询问
4. ❌ **与Scheduler设计不符** - TaskDescription是给子图看的，不是给用户看的

---

## ✅ 验证检查清单

- [x] MainGraphState添加getSubgraphContext()方法
- [x] MainGraphState添加getSubgraphTaskDescription()方法
- [x] UserInput节点优先读取subgraphContext
- [x] UserInput节点有降级策略（context → taskDescription → 默认）
- [x] SchedulerResponseParser的VALID_NEXT_NODES包含userInput
- [x] 所有linter错误已修复
- [x] 代码编译通过
- [x] 日志输出完善

---

## 📊 对比总结表

| 方面 | 修改前 | 修改后 |
|------|--------|--------|
| **UserInput输出** | 默认消息"等待用户输入" | Scheduler的context（详细指令） |
| **用户体验** | 不清楚要提供什么 | 清晰知道要提供什么信息 |
| **信息详细度** | 最低 | 高（包含要求、示例、说明） |
| **数据来源** | 硬编码 | 动态从Scheduler获取 |
| **降级策略** | 无 | context → taskDescription → 默认 |
| **与Scheduler对齐** | 不对齐 | 完全对齐 |

---

## 🎉 总结

感谢您的正确建议！现在**UserInput节点输出Scheduler的context**，这样：

1. ✅ **用户能看到详细的输入提示**
2. ✅ **Scheduler的context得到充分利用**
3. ✅ **减少不必要的多轮交互**
4. ✅ **提高整体用户体验**
5. ✅ **与Scheduler的职责完美对齐**

**UserInput节点现已完美实现！** 🎊






