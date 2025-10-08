// 全局变量
let currentSessionId = null;
let messageCount = 0;
let isLoading = false;
let sseConnection = null;
let fallbackPolling = false;

// DOM元素
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const sessionIdDisplay = document.getElementById('sessionId');
const messageCountDisplay = document.getElementById('messageCount');
const clearChatButton = document.getElementById('clearChat');
const systemStatusButton = document.getElementById('systemStatus');
const statusModal = document.getElementById('statusModal');
const loadingIndicator = document.getElementById('loadingIndicator');

// 初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeSession();
    setupEventListeners();
    updateUI();
});

// 初始化会话
function initializeSession() {
    currentSessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    sessionIdDisplay.textContent = `会话ID: ${currentSessionId}`;
    console.log('Session initialized:', currentSessionId);
    
    // 建立SSE连接
    establishSSEConnection();
}

// 建立SSE连接
function establishSSEConnection() {
    if (sseConnection) {
        sseConnection.close();
    }
    
    sseConnection = new EventSource(`/api/agent/sse/${currentSessionId}`);
    
    sseConnection.onopen = function(event) {
        console.log('SSE connection established');
    };
    
    sseConnection.onmessage = function(event) {
        try {
            const data = JSON.parse(event.data);
            handleSSEMessage(data);
        } catch (e) {
            console.error('Error parsing SSE message:', e);
        }
    };
    
    sseConnection.addEventListener('async_result', function(event) {
        try {
            const data = JSON.parse(event.data);
            handleAsyncResult(data);
        } catch (e) {
            console.error('Error parsing async result:', e);
        }
    });
    
    sseConnection.addEventListener('heartbeat', function(event) {
        try {
            const data = JSON.parse(event.data);
            console.log('Received heartbeat:', data.message);
        } catch (e) {
            console.error('Error parsing heartbeat:', e);
        }
    });
    
    sseConnection.onerror = function(event) {
        console.error('SSE connection error:', event);
        showPollingLoading(false);
        
        // 尝试重连
        setTimeout(() => {
            if (sseConnection.readyState === EventSource.CLOSED) {
                console.log('Attempting to reconnect SSE...');
                establishSSEConnection();
            }
        }, 3000);
    };
}

// 处理SSE消息
function handleSSEMessage(data) {
    console.log('Received SSE message:', data);
    
    switch (data.type) {
        case 'connection':
            console.log('SSE connection confirmed:', data.message);
            break;
        case 'async_result':
            handleAsyncResult(data);
            break;
        default:
            console.log('Unknown SSE message type:', data.type);
    }
}

// 处理异步结果
function handleAsyncResult(data) {
    showPollingLoading(false);
    
    if (data.message) {
        // 替换等待消息为最终结果
        replaceLastBotMessage(data.message);
    } else {
        addMessage('任务已完成，但未返回结果', 'bot', 'error');
    }
}

// 设置事件监听器
function setupEventListeners() {
    // 发送消息
    sendButton.addEventListener('click', sendMessage);
    
    // 回车发送消息
    messageInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    
    // 自动调整输入框高度
    messageInput.addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = Math.min(this.scrollHeight, 120) + 'px';
    });
    
    // 清空对话
    clearChatButton.addEventListener('click', clearChat);
    
    // 系统状态
    systemStatusButton.addEventListener('click', showSystemStatus);
    
    // 建议按钮
    document.querySelectorAll('.suggestion-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            const text = this.getAttribute('data-text');
            messageInput.value = text;
            messageInput.focus();
        });
    });
    
    // 模态框关闭
    document.querySelector('.close').addEventListener('click', function() {
        statusModal.style.display = 'none';
    });
    
    window.addEventListener('click', function(e) {
        if (e.target === statusModal) {
            statusModal.style.display = 'none';
        }
    });
}

// 发送消息
async function sendMessage() {
    const message = messageInput.value.trim();
    if (!message || isLoading) return;
    
    // 显示用户消息
    addMessage(message, 'user');
    messageInput.value = '';
    messageInput.style.height = 'auto';
    
    // 显示加载状态
    showLoading(true);
    
    try {
        const response = await fetch('/api/agent/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                message: message,
                sessionId: currentSessionId
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            addMessage(data.response, 'bot');
            
            // 如果返回的是等待消息，显示轮询加载状态
            if (data.response.includes('正在为您调用') || data.response.includes('请稍候')) {
                showPollingLoading(true);
                
                // 如果SSE连接失败，启动备用轮询机制
                if (sseConnection && sseConnection.readyState === EventSource.CLOSED) {
                    startFallbackPolling();
                }
            }
        } else {
            addMessage(`❌ 错误: ${data.error}`, 'bot', 'error');
        }
        
    } catch (error) {
        console.error('Error sending message:', error);
        addMessage(`❌ 发送消息失败: ${error.message}`, 'bot', 'error');
    } finally {
        showLoading(false);
    }
}

// 备用轮询机制（当SSE连接失败时使用）
async function startFallbackPolling() {
    if (fallbackPolling) return; // 避免重复启动
    
    fallbackPolling = true;
    const maxAttempts = 60; // 最多轮询60次，每次2秒，总共2分钟
    let attempts = 0;
    
    console.log('Starting fallback polling mechanism...');
    
    const pollInterval = setInterval(async () => {
        attempts++;
        
        try {
            const response = await fetch(`/api/agent/async-result/${currentSessionId}`);
            const data = await response.json();
            
            if (data.completed) {
                clearInterval(pollInterval);
                showPollingLoading(false);
                fallbackPolling = false;
                
                if (data.result) {
                    // 替换等待消息为最终结果
                    replaceLastBotMessage(data.result);
                } else {
                    addMessage('任务已完成，但未返回结果', 'bot', 'error');
                }
            } else if (attempts >= maxAttempts) {
                clearInterval(pollInterval);
                showPollingLoading(false);
                fallbackPolling = false;
                addMessage('⏰ 任务处理超时，请重试', 'bot', 'error');
            }
            
        } catch (error) {
            console.error('Error in fallback polling:', error);
            if (attempts >= maxAttempts) {
                clearInterval(pollInterval);
                showPollingLoading(false);
                fallbackPolling = false;
                addMessage('❌ 获取结果失败，请重试', 'bot', 'error');
            }
        }
    }, 2000); // 每2秒轮询一次
}

// 替换最后一条机器人消息
function replaceLastBotMessage(newContent) {
    const messages = chatMessages.querySelectorAll('.bot-message');
    if (messages.length > 0) {
        const lastMessage = messages[messages.length - 1];
        const messageBubble = lastMessage.querySelector('.message-bubble');
        if (messageBubble) {
            const formattedContent = formatMessage(newContent);
            messageBubble.innerHTML = formattedContent;
        }
    }
}

// 添加消息到聊天区域
function addMessage(content, sender, type = 'normal') {
    messageCount++;
    updateMessageCount();
    
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${sender}-message`;
    
    if (type === 'error') {
        messageDiv.classList.add('error-message');
    } else if (type === 'success') {
        messageDiv.classList.add('success-message');
    }
    
    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.innerHTML = sender === 'user' ? '<i class="fas fa-user"></i>' : '<i class="fas fa-robot"></i>';
    
    const messageContent = document.createElement('div');
    messageContent.className = 'message-content';
    
    const messageBubble = document.createElement('div');
    messageBubble.className = 'message-bubble';
    
    // 处理消息内容（支持简单的Markdown样式）
    const formattedContent = formatMessage(content);
    messageBubble.innerHTML = formattedContent;
    
    const messageTime = document.createElement('div');
    messageTime.className = 'message-time';
    messageTime.textContent = new Date().toLocaleTimeString();
    
    messageContent.appendChild(messageBubble);
    messageContent.appendChild(messageTime);
    messageDiv.appendChild(avatar);
    messageDiv.appendChild(messageContent);
    
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

// 格式化消息内容
function formatMessage(content) {
    // 转义HTML
    content = content.replace(/</g, '&lt;').replace(/>/g, '&gt;');
    
    // 换行
    content = content.replace(/\n/g, '<br>');
    
    // 粗体
    content = content.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    
    // 斜体
    content = content.replace(/\*(.*?)\*/g, '<em>$1</em>');
    
    // 代码块
    content = content.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');
    
    // 内联代码
    content = content.replace(/`([^`]+)`/g, '<code>$1</code>');
    
    return content;
}

// 滚动到底部
function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// 显示/隐藏加载状态
function showLoading(show) {
    isLoading = show;
    loadingIndicator.style.display = show ? 'block' : 'none';
    sendButton.disabled = show;
    
    if (show) {
        sendButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    } else {
        sendButton.innerHTML = '<i class="fas fa-paper-plane"></i>';
    }
}

// 显示/隐藏轮询加载状态（不阻塞发送按钮）
function showPollingLoading(show) {
    loadingIndicator.style.display = show ? 'block' : 'none';
    if (show) {
        loadingIndicator.querySelector('p').textContent = '智能体正在处理中，请稍候...';
    }
}

// 更新UI
function updateUI() {
    updateMessageCount();
}

// 更新消息计数
function updateMessageCount() {
    messageCountDisplay.textContent = `消息数: ${messageCount}`;
}

// 清空对话
function clearChat() {
    if (confirm('确定要清空当前对话吗？')) {
        // 清空消息区域（保留欢迎消息）
        const welcomeMessage = chatMessages.querySelector('.welcome-message');
        chatMessages.innerHTML = '';
        if (welcomeMessage) {
            chatMessages.appendChild(welcomeMessage);
        }
        
        // 重置计数
        messageCount = 0;
        updateMessageCount();
        
        // 清空服务器端会话历史
        clearServerSession();
        
        console.log('Chat cleared');
    }
}

// 清空服务器端会话历史
async function clearServerSession() {
    try {
        await fetch(`/api/agent/testState?requestId=${currentSessionId}`, {
            method: 'GET'
        });
        console.log('Server session cleared');
    } catch (error) {
        console.error('Error clearing server session:', error);
    }
}

// 显示系统状态
async function showSystemStatus() {
    statusModal.style.display = 'block';
    
    // 重置状态显示
    document.getElementById('serviceStatus').textContent = '正在检查...';
    document.getElementById('sessionStatus').textContent = '正在加载...';
    document.getElementById('aiModelStatus').textContent = '正在检查...';
    
    try {
        // 检查服务状态
        await checkServiceStatus();
        
        // 检查会话状态
        await checkSessionStatus();
        
        // 检查AI模型状态
        await checkAIModelStatus();
        
    } catch (error) {
        console.error('Error checking system status:', error);
    }
}

// 检查服务状态
async function checkServiceStatus() {
    try {
        const response = await fetch('/api/system/status');
        const data = await response.json();
        
        let statusHtml = `
            <div><strong>应用名称:</strong> ${data.applicationName || 'N/A'}</div>
            <div><strong>服务端口:</strong> ${data.serverPort || 'N/A'}</div>
            <div><strong>Bean总数:</strong> ${data.totalBeans || 'N/A'}</div>
            <div><strong>状态:</strong> <span style="color: green;">✅ 正常运行</span></div>
        `;
        
        if (data.components) {
            statusHtml += '<div><strong>核心组件:</strong></div>';
            for (const [component, status] of Object.entries(data.components)) {
                const icon = status ? '✅' : '❌';
                statusHtml += `<div>  ${icon} ${component}: ${status ? '正常' : '异常'}</div>`;
            }
        }
        
        document.getElementById('serviceStatus').innerHTML = statusHtml;
    } catch (error) {
        document.getElementById('serviceStatus').innerHTML = `<div style="color: red;">❌ 检查失败: ${error.message}</div>`;
    }
}

// 检查会话状态
function checkSessionStatus() {
    const statusHtml = `
        <div><strong>当前会话ID:</strong> ${currentSessionId}</div>
        <div><strong>消息数量:</strong> ${messageCount}</div>
        <div><strong>会话开始时间:</strong> ${new Date(parseInt(currentSessionId.split('_')[1])).toLocaleString()}</div>
        <div><strong>记忆状态:</strong> <span style="color: green;">✅ 已启用</span></div>
    `;
    document.getElementById('sessionStatus').innerHTML = statusHtml;
}

// 检查AI模型状态
async function checkAIModelStatus() {
    try {
        const response = await fetch('/api/agent/ollama/status');
        const statusText = await response.text();
        
        let statusHtml;
        if (statusText.includes('✅')) {
            statusHtml = `<div style="color: green;">${statusText}</div>`;
        } else {
            statusHtml = `<div style="color: red;">${statusText}</div>`;
        }
        
        // 获取详细诊断信息
        try {
            const diagnoseResponse = await fetch('/api/agent/ollama/diagnose');
            const diagnoseText = await diagnoseResponse.text();
            statusHtml += `<div style="margin-top: 10px;"><strong>详细诊断:</strong></div>`;
            statusHtml += `<pre style="font-size: 0.8rem; white-space: pre-wrap;">${diagnoseText}</pre>`;
        } catch (e) {
            // 诊断信息获取失败，忽略
        }
        
        document.getElementById('aiModelStatus').innerHTML = statusHtml;
    } catch (error) {
        document.getElementById('aiModelStatus').innerHTML = `<div style="color: red;">❌ 检查失败: ${error.message}</div>`;
    }
}

// 工具函数：生成UUID
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

// 错误处理
window.addEventListener('error', function(e) {
    console.error('JavaScript error:', e.error);
    addMessage(`⚠️ 页面发生错误: ${e.error.message}`, 'bot', 'error');
});

// 网络状态监听
window.addEventListener('online', function() {
    addMessage('🌐 网络连接已恢复', 'bot', 'success');
});

window.addEventListener('offline', function() {
    addMessage('⚠️ 网络连接已断开', 'bot', 'error');
});

// 页面卸载前提醒
window.addEventListener('beforeunload', function(e) {
    if (messageCount > 0) {
        e.preventDefault();
        e.returnValue = '确定要离开吗？当前对话记录将会保存在服务器中。';
    }
    
    // 关闭SSE连接
    if (sseConnection) {
        sseConnection.close();
    }
});

