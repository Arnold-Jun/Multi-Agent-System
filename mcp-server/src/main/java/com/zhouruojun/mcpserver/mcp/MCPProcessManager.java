package com.zhouruojun.mcpserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP进程管理器
 * 管理本地可执行文件形式的MCP服务器
 */
@Slf4j
@Component
public class MCPProcessManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private PortManager portManager;
    
    // 运行中的进程：服务器名称 -> 进程信息
    private final Map<String, MCPProcess> runningProcesses = new ConcurrentHashMap<>();
    
    // 进程使用的端口：服务器名称 -> 端口号
    private final Map<String, Integer> processPorts = new ConcurrentHashMap<>();
    
    /**
     * MCP进程信息
     */
    public static class MCPProcess {
        private final Process process;
        private final PrintWriter stdin;
        private final BufferedReader stdout;
        private final String serverName;
        private final long startTime;
        
        public MCPProcess(Process process, PrintWriter stdin, BufferedReader stdout, String serverName) {
            this.process = process;
            this.stdin = stdin;
            this.stdout = stdout;
            this.serverName = serverName;
            this.startTime = System.currentTimeMillis();
        }
        
        // Getters
        public Process getProcess() { return process; }
        public PrintWriter getStdin() { return stdin; }
        public BufferedReader getStdout() { return stdout; }
        public String getServerName() { return serverName; }
        public long getStartTime() { return startTime; }
        
        public boolean isAlive() {
            return process.isAlive();
        }
        
        public void destroy() {
            try {
                log.info("开始关闭进程: {} (PID: {})", serverName, process.pid());
                
                if (stdin != null) {
                    stdin.close();
                }
                if (stdout != null) {
                    stdout.close();
                }
                
                process.destroy();
                
                boolean terminated = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                
                if (!terminated) {
                    log.warn("进程未在5秒内关闭，强制终止: {} (PID: {})", serverName, process.pid());
                    process.destroyForcibly();
                    process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                }
                
            } catch (Exception e) {
                log.error("关闭进程异常: {} (PID: {})", serverName, process.pid(), e);
            }
        }
    }
    
    /**
     * 启动MCP进程
     * 支持各种命令类型，包括npx、node、python等
     */
    public CompletableFuture<MCPProcess> startMCPProcess(String serverName, String command, List<String> args) {
        return startMCPProcess(serverName, command, args, null);
    }
    
    /**
     * 启动MCP进程（带工作目录）
     * 支持各种命令类型，包括npx、node、python等
     */
    public CompletableFuture<MCPProcess> startMCPProcess(String serverName, String command, List<String> args, String workingDirectory) {
        return startMCPProcess(serverName, command, args, workingDirectory, null);
    }
    
    /**
     * 启动MCP进程（带工作目录和环境变量）
     * 支持各种命令类型，包括npx、node、python等
     */
    public CompletableFuture<MCPProcess> startMCPProcess(String serverName, String command, List<String> args, String workingDirectory, Map<String, String> environmentVariables) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 构建进程
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command().add(command);
                if (args != null) {
                    processBuilder.command().addAll(args);
                }
                
                File workingDir;
                if (workingDirectory != null && !workingDirectory.trim().isEmpty()) {
                    workingDir = new File(workingDirectory);
                    if (!workingDir.exists()) {
                        log.warn("指定的工作目录不存在: {}，使用默认目录", workingDirectory);
                        workingDir = new File(System.getProperty("user.home"));
                    }
                } else {
                    workingDir = new File(System.getProperty("user.home"));
                }
                processBuilder.directory(workingDir);
                
                Map<String, String> env = processBuilder.environment();

                // 设置基础环境变量
                env.put("PATH", System.getenv("PATH"));
                
                // 设置自定义环境变量
                if (environmentVariables != null && !environmentVariables.isEmpty()) {
                    log.info("设置自定义环境变量: {}", environmentVariables.keySet());
                    env.putAll(environmentVariables);
                }
                
                // 为特定命令设置特殊环境
                if ("npx".equals(command)) {
                    setupNpxEnvironment(env);
                    log.info("设置npx环境变量，PATH: {}", env.get("PATH"));
                    
                    // 尝试查找npx命令的完整路径
                    String npxPath = findNpxPath(env);
                    if (npxPath != null) {
                        log.info("找到npx命令: {}", npxPath);
                        // 使用完整路径替换命令
                        processBuilder.command().set(0, npxPath);
                    } else {
                        log.warn("未找到npx命令，将尝试直接运行npx");
                    }
                }
                
                Process process = processBuilder.start();
                Thread.sleep(1000);
                
                if (!process.isAlive()) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    StringBuilder errorOutput = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    errorReader.close();
                    
                    String errorMsg = errorOutput.toString();
                    if (errorMsg.contains("Cannot run program") && errorMsg.contains("npx")) {
                        throw new RuntimeException("npx命令不可用，请确保已安装Node.js并添加到PATH环境变量中。\n" +
                            "常见解决方案：\n" +
                            "1. 安装Node.js: https://nodejs.org/\n" +
                            "2. 检查PATH环境变量\n" +
                            "3. 重启命令行或IDE。\n" +
                            "原始错误: " + errorMsg);
                    } else {
                        throw new RuntimeException("进程启动失败: " + errorMsg);
                    }
                }
                
                PrintWriter stdin = new PrintWriter(process.getOutputStream(), true);
                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                MCPProcess mcpProcess = new MCPProcess(process, stdin, stdout, serverName);
                runningProcesses.put(serverName, mcpProcess);
                
                Thread.sleep(2500);
                
                if (!process.isAlive()) {
                    runningProcesses.remove(serverName);
                    throw new RuntimeException("进程在初始化期间退出: " + serverName);
                }
                
                return mcpProcess;
                
            } catch (Exception e) {
                log.error("启动MCP进程失败: {}", serverName, e);
                throw new RuntimeException("启动MCP进程失败: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 停止MCP进程
     */
    public void stopMCPProcess(String serverName) {
        MCPProcess mcpProcess = runningProcesses.remove(serverName);
        if (mcpProcess != null) {
            mcpProcess.destroy();
            
            Integer port = processPorts.remove(serverName);
            if (port != null) {
                portManager.releasePort(port);
            }
        }
    }
    
    /**
     * 停止所有MCP进程
     */
    public void stopAllProcesses() {
        log.info("开始停止所有MCP进程，共 {} 个进程", runningProcesses.size());
        
        runningProcesses.values().forEach(MCPProcess::destroy);
        runningProcesses.clear();
        
        if (!processPorts.isEmpty()) {
            processPorts.forEach((serverName, port) -> portManager.releasePort(port));
            processPorts.clear();
        }
        
        portManager.clearAllPorts();
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("所有MCP进程已停止");
    }
    
    /**
     * 获取MCP进程
     */
    public MCPProcess getMCPProcess(String serverName) {
        return runningProcesses.get(serverName);
    }
    
    /**
     * 检查进程是否运行
     */
    public boolean isProcessRunning(String serverName) {
        MCPProcess mcpProcess = runningProcesses.get(serverName);
        if (mcpProcess == null) {
            return false;
        }
        
        boolean isAlive = mcpProcess.isAlive();
        if (!isAlive) {
            // 清理死进程
            log.info("清理死进程: {}", serverName);
            runningProcesses.remove(serverName);
        }
        
        return isAlive;
    }
    
    /**
     * 获取进程状态信息
     */
    public Map<String, Object> getProcessStatus(String serverName) {
        MCPProcess mcpProcess = runningProcesses.get(serverName);
        if (mcpProcess == null) {
            return Map.of("status", "not_found", "message", "进程不存在");
        }
        
        boolean isAlive = mcpProcess.isAlive();
        return Map.of(
            "status", isAlive ? "running" : "stopped",
            "pid", mcpProcess.getProcess().pid(),
            "startTime", mcpProcess.getStartTime(),
            "uptime", System.currentTimeMillis() - mcpProcess.getStartTime(),
            "alive", isAlive
        );
    }
    
    /**
     * 向MCP进程发送请求
     */
    public CompletableFuture<Map<String, Object>> sendRequest(String serverName, Map<String, Object> request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MCPProcess mcpProcess = runningProcesses.get(serverName);
                if (mcpProcess == null) {
                    log.error("MCP进程不存在: {}", serverName);
                    throw new RuntimeException("MCP进程不存在: " + serverName);
                }
                
                if (!mcpProcess.isAlive()) {
                    log.error("MCP进程已停止: {} (PID: {})", serverName, mcpProcess.getProcess().pid());
                    // 清理死进程
                    runningProcesses.remove(serverName);
                    throw new RuntimeException("MCP进程已停止: " + serverName);
                }
                
                String requestJson = objectMapper.writeValueAsString(request);
                log.debug("发送请求到进程: {}", serverName);
                
                mcpProcess.getStdin().println(requestJson);
                mcpProcess.getStdin().flush();
                
                // 使用超时机制读取响应
                String responseJson = readLineWithTimeout(mcpProcess.getStdout(), 10000); // 10秒超时
                if (responseJson == null) {
                    throw new RuntimeException("MCP进程无响应或超时: " + serverName);
                }
                
                log.debug("收到进程响应: {}", serverName);
                @SuppressWarnings("unchecked")
                Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
                return response;
                
            } catch (Exception e) {
                log.error("与MCP进程通信失败: {}", serverName, e);
                throw new RuntimeException("与MCP进程通信失败: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 带超时的读取行方法
     */
    private String readLineWithTimeout(java.io.BufferedReader reader, long timeoutMs) {
        try {
            long startTime = System.currentTimeMillis();
            StringBuilder line = new StringBuilder();
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (reader.ready()) {
                    int ch = reader.read();
                    if (ch == -1) {
                        return null; // EOF
                    }
                    if (ch == '\n') {
                        return line.toString();
                    }
                    line.append((char) ch);
                } else {
                    // 短暂休眠避免CPU占用过高
                    Thread.sleep(10);
                }
            }
            
            log.warn("读取进程响应超时: {}ms", timeoutMs);
            return null;
        } catch (Exception e) {
            log.error("读取进程响应异常", e);
            return null;
        }
    }

    
    /**
     * 查找npx命令的完整路径
     */
    private String findNpxPath(Map<String, String> env) {
        try {
            // 在Windows上尝试使用where命令查找npx
            ProcessBuilder whereBuilder = new ProcessBuilder("where", "npx");
            whereBuilder.environment().putAll(env);
            Process whereProcess = whereBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(whereProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // 在Windows上，优先使用.cmd文件
                    if (line.endsWith(".cmd") || line.endsWith(".bat")) {
                        return line;
                    }
                }
            }
            
            whereProcess.waitFor();
        } catch (Exception e) {
            log.debug("查找npx路径失败: {}", e.getMessage());
        }
        
        // 如果where命令失败，尝试常见的npx路径
        String[] commonPaths = {
            "C:\\Program Files\\nodejs\\npx.cmd",
            "C:\\Program Files (x86)\\nodejs\\npx.cmd",
            System.getProperty("user.home") + "\\AppData\\Roaming\\npm\\npx.cmd",
            System.getProperty("user.home") + "\\AppData\\Local\\npm\\npx.cmd"
        };
        
        for (String path : commonPaths) {
            if (new File(path).exists()) {
                return path;
            }
        }
        
        return null;
    }
    
    
    /**
     * 为npx命令设置特殊的环境变量
     */
    private void setupNpxEnvironment(Map<String, String> env) {
        env.put("NODE_ENV", "production");
        env.put("NPM_CONFIG_LOGLEVEL", "error");
        env.put("NPM_CONFIG_PROGRESS", "false");
        env.put("NPM_CONFIG_AUDIT", "false");
        
        String npmCacheDir = System.getProperty("user.home") + File.separator + ".npm";
        env.put("NPM_CONFIG_CACHE", npmCacheDir);
        
        // 设置Node.js和npm的路径
        String currentPath = env.get("PATH");
        if (currentPath != null) {
            // 常见的Node.js安装路径
            String[] nodePaths = {
                "C:\\Program Files\\nodejs",
                "C:\\Program Files (x86)\\nodejs",
                System.getProperty("user.home") + "\\AppData\\Roaming\\npm",
                System.getProperty("user.home") + "\\AppData\\Local\\npm"
            };
            
            StringBuilder newPath = new StringBuilder();
            for (String nodePath : nodePaths) {
                if (new File(nodePath).exists()) {
                    newPath.append(nodePath).append(File.pathSeparator);
                }
            }
            newPath.append(currentPath);
            env.put("PATH", newPath.toString());
        }
        
        // 设置npm全局目录
        String npmGlobalDir = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "npm";
        env.put("NPM_CONFIG_PREFIX", npmGlobalDir);
    }
    
    /**
     * 获取运行中的进程统计
     */
    public Map<String, Object> getProcessStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalProcesses", runningProcesses.size());
        stats.put("processes", runningProcesses.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> Map.of(
                    "alive", entry.getValue().isAlive(),
                    "startTime", entry.getValue().getStartTime(),
                    "uptime", System.currentTimeMillis() - entry.getValue().getStartTime()
                )
            ))
        );
        return stats;
    }
    
    /**
     * 清理死进程
     */
    public void cleanupDeadProcesses() {
        runningProcesses.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) {
                String serverName = entry.getKey();
                Integer port = processPorts.remove(serverName);
                if (port != null) {
                    portManager.releasePort(port);
                }
                return true;
            }
            return false;
        });
    }
    
    /**
     * 为进程分配端口
     */
    public int allocatePortForProcess(String serverName) {
        int port = portManager.getAvailablePort();
        processPorts.put(serverName, port);
        return port;
    }
    
    /**
     * 获取进程使用的端口
     */
    public Integer getProcessPort(String serverName) {
        return processPorts.get(serverName);
    }
    
    /**
     * 强制清理端口占用
     * 通过系统命令查找并终止占用指定端口的进程
     */
    public void forceCleanupPort(int port) {
        try {
            log.info("强制清理端口: {}", port);
            
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                ProcessBuilder findProcess = new ProcessBuilder(
                    "cmd", "/c", "netstat", "-ano", "|", "findstr", ":" + port
                );
                Process findProc = findProcess.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(findProc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(":" + port) && line.contains("LISTENING")) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length >= 5) {
                                String pid = parts[parts.length - 1];
                                try {
                                    int processId = Integer.parseInt(pid);
                                    log.info("发现占用端口 {} 的进程 PID: {}", port, processId);
                                    
                                    ProcessBuilder killProcess = new ProcessBuilder(
                                        "cmd", "/c", "taskkill", "/F", "/PID", String.valueOf(processId)
                                    );
                                    Process killProc = killProcess.start();
                                    int exitCode = killProc.waitFor();
                                    
                                    if (exitCode == 0) {
                                        log.info("成功终止占用端口 {} 的进程 PID: {}", port, processId);
                                    } else {
                                        log.warn("终止进程失败 PID: {}, 退出码: {}", processId, exitCode);
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("无法解析PID: {}", pid);
                                }
                            }
                        }
                    }
                }
                
                findProc.waitFor();
            } else {
                ProcessBuilder findProcess = new ProcessBuilder(
                    "lsof", "-ti", ":" + port
                );
                Process findProc = findProcess.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(findProc.getInputStream()))) {
                    String pid = reader.readLine();
                    if (pid != null && !pid.trim().isEmpty()) {
                        log.info("发现占用端口 {} 的进程 PID: {}", port, pid);
                        
                        ProcessBuilder killProcess = new ProcessBuilder("kill", "-9", pid.trim());
                        Process killProc = killProcess.start();
                        int exitCode = killProc.waitFor();
                        
                        if (exitCode == 0) {
                            log.info("成功终止占用端口 {} 的进程 PID: {}", port, pid);
                        } else {
                            log.warn("终止进程失败 PID: {}, 退出码: {}", pid, exitCode);
                        }
                    }
                }
                
                findProc.waitFor();
            }
            
        } catch (Exception e) {
            log.error("强制清理端口 {} 失败", port, e);
        }
    }
    
    /**
     * 强制清理所有常用端口
     */
    public void forceCleanupAllPorts() {
        int[] commonPorts = {18060, 18061, 18062, 18063, 18064, 18065, 18066, 18067, 18068, 18069, 18070};
        
        for (int port : commonPorts) {
            if (!portManager.isPortAvailable(port)) {
                log.warn("端口 {} 被占用，尝试强制清理", port);
                forceCleanupPort(port);
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (portManager.isPortAvailable(port)) {
                    log.info("端口 {} 已释放", port);
                } else {
                    log.warn("端口 {} 仍然被占用", port);
                }
            }
        }
    }
}
