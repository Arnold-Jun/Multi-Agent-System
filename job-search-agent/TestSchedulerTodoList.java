import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import com.zhouruojun.jobsearchagent.agent.todo.TodoList;
import com.zhouruojun.jobsearchagent.agent.todo.TodoTask;
import com.zhouruojun.jobsearchagent.agent.todo.TaskStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试Scheduler TodoList信息生成
 */
public class TestSchedulerTodoList {
    
    public static void main(String[] args) {
        // 创建测试用的TodoList
        TodoList todoList = new TodoList("寻找Java开发工程师岗位");
        
        // 添加测试任务
        TodoTask task1 = new TodoTask();
        task1.setTaskId("task_001");
        task1.setDescription("搜索Java开发工程师岗位信息");
        task1.setAssignedAgent("jobInfoCollectorAgent");
        task1.setStatus(TaskStatus.PENDING);
        todoList.addTask(task1);
        
        TodoTask task2 = new TodoTask();
        task2.setTaskId("task_002");
        task2.setDescription("分析并优化简历内容");
        task2.setAssignedAgent("resumeAnalysisOptimizationAgent");
        task2.setStatus(TaskStatus.IN_PROGRESS);
        todoList.addTask(task2);
        
        TodoTask task3 = new TodoTask();
        task3.setTaskId("task_003");
        task3.setDescription("投递简历到目标公司");
        task3.setAssignedAgent("jobSearchExecutionAgent");
        task3.setStatus(TaskStatus.COMPLETED);
        todoList.addTask(task3);
        
        TodoTask task4 = new TodoTask();
        task4.setTaskId("task_004");
        task4.setDescription("准备面试材料");
        task4.setAssignedAgent("jobSearchExecutionAgent");
        task4.setStatus(TaskStatus.FAILED);
        task4.setFailureCount(2);
        todoList.addTask(task4);
        
        // 创建MainGraphState并设置TodoList
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("todoList", todoList);
        MainGraphState state = new MainGraphState(stateMap);
        
        // 测试新的方法
        System.out.println("=== 测试getTodoListForScheduler方法 ===\n");
        String schedulerInfo = state.getTodoListForScheduler();
        System.out.println(schedulerInfo);
        
        System.out.println("=== 对比原来的getTodoListStatistics方法 ===\n");
        String statistics = state.getTodoListStatistics();
        System.out.println(statistics);
    }
}
