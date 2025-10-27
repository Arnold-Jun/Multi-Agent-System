/**
 * 旅游智能体前端交互脚本
 * 实现聊天界面、API调用、状态管理等功能
 */

class TravelingAgentApp {
    constructor() {
        this.currentSessionId = this.generateSessionId();
        this.isLoading = false;
        this.messageHistory = [];
        this.settings = {
            model: 'qwen3:8b',
            temperature: 0.7,
            maxTokens: 4096
        };
        this.travelVisualization = null;
        this.waitingForUserInput = false; // 新增：用户输入状态
        this.userInputPrompt = ''; // 新增：用户输入提示
        
        // WebSocket 相关
        this.stompClient = null;
        this.connected = false;
        this.reconnectAttempts = 0; // 重连尝试次数
        this.maxReconnectAttempts = 3; // 最大重连次数
        this.reconnectTimeout = null; // 重连定时器
        this.processedMessages = new Set(); // 已处理消息ID集合，用于去重
        this.connectionCounter = 0; // 连接计数器，用于调试
        this.subscriptions = []; // 订阅管理，用于清理
        
        this.init();
    }

    init() {
        this.bindEvents();
        this.loadSettings();
        this.initWebSocket();
        this.loadChatHistory();
        this.setupAutoResize();
        this.initTravelVisualization();
        
        // 定期检查智能体状态
        setInterval(() => this.checkAgentStatus(), 30000);
    }

    bindEvents() {
        // 发送消息
        const sendBtn = document.getElementById('sendBtn');
        const messageInput = document.getElementById('messageInput');
        
        sendBtn.addEventListener('click', () => this.sendMessage());
        messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });

        // 快速操作按钮
        document.querySelectorAll('.quick-action-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const prompt = e.target.getAttribute('data-prompt');
                messageInput.value = prompt;
                this.sendMessage();
            });
        });

        // 新对话按钮
        document.getElementById('newChatBtn').addEventListener('click', () => {
            this.startNewChat();
        });

        // 设置相关
        document.getElementById('settingsBtn').addEventListener('click', () => {
            this.showSettingsModal();
        });

        document.getElementById('closeSettingsModal').addEventListener('click', () => {
            this.hideSettingsModal();
        });

        document.getElementById('saveSettings').addEventListener('click', () => {
            this.saveSettings();
        });

        document.getElementById('cancelSettings').addEventListener('click', () => {
            this.hideSettingsModal();
        });

        // 温度滑块
        const temperatureSlider = document.getElementById('temperatureSlider');
        const temperatureValue = document.getElementById('temperatureValue');
        temperatureSlider.addEventListener('input', (e) => {
            temperatureValue.textContent = e.target.value;
        });

        // 文件上传相关
        document.getElementById('attachBtn').addEventListener('click', () => {
            this.showUploadModal();
        });

        document.getElementById('closeUploadModal').addEventListener('click', () => {
            this.hideUploadModal();
        });

        document.getElementById('cancelUpload').addEventListener('click', () => {
            this.hideUploadModal();
        });

        // 文件拖拽上传
        const uploadArea = document.getElementById('uploadArea');
        const fileInput = document.getElementById('fileInput');
        
        uploadArea.addEventListener('click', () => fileInput.click());
        uploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadArea.style.borderColor = 'var(--primary-color)';
            uploadArea.style.backgroundColor = 'rgba(59, 130, 246, 0.05)';
        });
        
        uploadArea.addEventListener('dragleave', (e) => {
            e.preventDefault();
            uploadArea.style.borderColor = 'var(--border-color)';
            uploadArea.style.backgroundColor = 'transparent';
        });
        
        uploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadArea.style.borderColor = 'var(--border-color)';
            uploadArea.style.backgroundColor = 'transparent';
            this.handleFileDrop(e.dataTransfer.files);
        });

        fileInput.addEventListener('change', (e) => {
            this.handleFileSelect(e.target.files);
        });

        // 导出功能
        document.getElementById('exportBtn').addEventListener('click', () => {
            this.exportChat();
        });

        // WebSocket测试按钮
        document.getElementById('testWsBtn').addEventListener('click', () => {
            this.testWebSocketConnection();
        });

        // 历史记录管理功能
        document.getElementById('clearAllHistoryBtn').addEventListener('click', () => {
            this.clearAllHistory();
        });

        document.getElementById('refreshHistoryBtn').addEventListener('click', () => {
            this.loadChatHistory();
            this.showNotification('历史记录已刷新', 'success');
        });

        // 测试换行符处理（开发调试用）
        if (window.location.search.includes('debug=true')) {
            const testBtn = document.createElement('button');
            testBtn.textContent = '测试换行符';
            testBtn.style.cssText = `
                position: fixed;
                top: 10px;
                right: 10px;
                z-index: 1000;
                background: var(--error-color);
                color: white;
                border: none;
                padding: 8px 12px;
                border-radius: 4px;
                cursor: pointer;
            `;
            testBtn.addEventListener('click', () => {
                this.testLineBreakHandling();
            });
            document.body.appendChild(testBtn);
        }

        // 模态框点击外部关闭
        document.querySelectorAll('.modal').forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    modal.classList.remove('show');
                }
            });
        });

        // 页面卸载时清理WebSocket连接
        window.addEventListener('beforeunload', () => {
            this.cleanupWebSocket();
        });

        // 页面隐藏时暂停重连
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                // 页面隐藏时暂停重连
                if (this.reconnectTimeout) {
                    clearTimeout(this.reconnectTimeout);
                    this.reconnectTimeout = null;
                }
            } else if (!this.connected) {
                // 页面重新可见时恢复重连
                this.scheduleReconnect();
            }
        });
    }

    setupAutoResize() {
        const messageInput = document.getElementById('messageInput');
        messageInput.addEventListener('input', () => {
            messageInput.style.height = 'auto';
            messageInput.style.height = Math.min(messageInput.scrollHeight, 120) + 'px';
        });
    }

    generateSessionId() {
        return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    }

    /**
     * 生成消息唯一ID用于去重
     */
    generateMessageId(message) {
        // 使用消息内容、类型、会话ID生成唯一ID
        const content = message.content || message.prompt || message.error || '';
        const type = message.type || 'unknown';
        const sessionId = message.sessionId || this.currentSessionId;
        
        // 创建基于内容的哈希值，确保相同内容生成相同ID
        const hash = this.simpleHash(content + type + sessionId);
        const messageId = `${type}_${sessionId}_${hash}`;
        
        console.log('🔑 生成消息ID详情:');
        console.log('  - 内容长度:', content.length);
        console.log('  - 类型:', type);
        console.log('  - 会话ID:', sessionId);
        console.log('  - 哈希值:', hash);
        console.log('  - 最终ID:', messageId);
        
        return messageId;
    }

    /**
     * 简单的哈希函数
     */
    simpleHash(str) {
        let hash = 0;
        if (str.length === 0) return hash;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32bit integer
        }
        return Math.abs(hash).toString(36);
    }

    initWebSocket() {
        try {
            console.log('正在初始化 WebSocket 连接...');
            
            // 清理之前的连接
            this.cleanupWebSocket();
            
            // 使用 SockJS 和 STOMP
            // 自动检测当前页面的端口
            const protocol = window.location.protocol;
            const hostname = window.location.hostname;
            const port = window.location.port || (protocol === 'https:' ? '443' : '80');
            const wsUrl = `${protocol}//${hostname}:${port}/ws`;
            
            console.log('WebSocket连接URL:', wsUrl);
            const socket = new SockJS(wsUrl);
            this.stompClient = Stomp.over(socket);
            
            // 启用调试日志（临时）
            this.stompClient.debug = function(str) {
                console.log('STOMP Debug:', str);
            };
            
            // 设置连接选项
            const connectOptions = {
                timeout: 10000, // 10秒超时
                heartbeat_in: 0,
                heartbeat_out: 20000,
                debug: true
            };
            
            // 连接 WebSocket
            this.connectionCounter++;
            console.log(`🔗 第${this.connectionCounter}次WebSocket连接尝试`);
            
            this.stompClient.connect(connectOptions, (frame) => {
                console.log(`✅ WebSocket 连接成功 (第${this.connectionCounter}次):`, frame);
                this.connected = true;
                this.reconnectAttempts = 0; // 重置重连次数
                this.updateConnectionStatus(true);
                
                // 清理之前的订阅
                this.cleanupSubscriptions();
                
                // 订阅回复消息
                const replySubscription = this.stompClient.subscribe('/topic/reply', (message) => {
                    console.log('📨 收到回复消息:', message.body);
                    console.log('📨 当前会话ID:', this.currentSessionId);
                    try {
                        const data = JSON.parse(message.body);
                        console.log('📨 解析后的数据:', data);
                        // 只处理当前会话的消息
                        if (data.sessionId === this.currentSessionId) {
                            console.log('📨 会话ID匹配，处理消息');
                            this.handleWebSocketMessage(data);
                        } else {
                            console.log('📨 会话ID不匹配，忽略消息');
                        }
                    } catch (e) {
                        console.error('解析回复消息失败:', e);
                    }
                });
                this.subscriptions.push(replySubscription);
                
                // 订阅错误消息
                this.stompClient.subscribe('/topic/error', (message) => {
                    console.log('收到错误消息:', message.body);
                    try {
                        const data = JSON.parse(message.body);
                        // 只处理当前会话的消息
                        if (data.sessionId === this.currentSessionId) {
                            this.handleWebSocketError(data);
                        }
                    } catch (e) {
                        console.error('解析错误消息失败:', e);
                    }
                });
                
                // 订阅用户输入请求消息
                this.stompClient.subscribe('/topic/userInput', (message) => {
                    console.log('收到用户输入请求:', message.body);
                    try {
                        const data = JSON.parse(message.body);
                        if (data.sessionId === this.currentSessionId) {
                            this.handleUserInputRequest(data.prompt || data.content);
                        }
                    } catch (e) {
                        console.error('解析用户输入请求失败:', e);
                    }
                });
                
            }, (error) => {
                console.error('WebSocket 连接失败:', error);
                this.connected = false;
                this.updateConnectionStatus(false);
                
                // 智能重连机制
                this.scheduleReconnect();
            });
            
        } catch (error) {
            console.error('WebSocket 初始化失败:', error);
            this.connected = false;
            this.updateConnectionStatus(false);
            
            // 智能重连机制
            this.scheduleReconnect();
        }
    }

    /**
     * 清理订阅
     */
    cleanupSubscriptions() {
        console.log('🧹 清理订阅，当前订阅数量:', this.subscriptions.length);
        this.subscriptions.forEach(subscription => {
            try {
                subscription.unsubscribe();
            } catch (e) {
                console.log('清理订阅时出错:', e);
            }
        });
        this.subscriptions = [];
        console.log('🧹 订阅清理完成');
    }

    /**
     * 清理WebSocket连接
     */
    cleanupWebSocket() {
        console.log('🧹 开始清理WebSocket连接...');
        
        // 先清理订阅
        this.cleanupSubscriptions();
        
        if (this.stompClient) {
            try {
                console.log('🧹 断开WebSocket连接...');
                this.stompClient.disconnect();
                console.log('🧹 WebSocket连接已断开');
            } catch (e) {
                console.log('清理WebSocket连接时出错:', e);
            }
            this.stompClient = null;
        }
        
        // 清理重连定时器
        if (this.reconnectTimeout) {
            console.log('🧹 清理重连定时器...');
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }
        
        this.connected = false;
        console.log('🧹 WebSocket清理完成');
    }

    /**
     * 智能重连机制
     */
    scheduleReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.log('已达到最大重连次数，停止重连');
            this.updateConnectionStatus(false);
            return;
        }
        
        this.reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts - 1), 30000); // 指数退避，最大30秒
        
        console.log(`第${this.reconnectAttempts}次重连尝试，${delay}ms后执行`);
        
        this.reconnectTimeout = setTimeout(() => {
            if (!this.connected) {
                console.log('执行重连...');
                this.initWebSocket();
            }
        }, delay);
    }

    updateConnectionStatus(connected) {
        const statusIndicator = document.getElementById('agentStatus');
        const statusDot = statusIndicator.querySelector('.status-dot');
        const statusText = statusIndicator.querySelector('.status-text');
        
        if (connected) {
            statusDot.className = 'status-dot';
            statusText.textContent = 'WebSocket 已连接';
            console.log('✅ WebSocket 连接状态更新为：已连接');
        } else {
            statusDot.className = 'status-dot offline';
            statusText.textContent = 'WebSocket 连接失败';
            console.log('❌ WebSocket 连接状态更新为：连接失败');
        }
    }

    // 添加WebSocket连接测试方法
    testWebSocketConnection() {
        console.log('🔍 开始测试 WebSocket 连接...');
        
        if (!this.stompClient) {
            console.log('❌ STOMP 客户端未初始化');
            return false;
        }
        
        if (!this.connected) {
            console.log('❌ WebSocket 未连接');
            return false;
        }
        
        try {
            // 发送ping测试
            this.stompClient.send('/app/traveling/ping', {}, 'ping');
            console.log('✅ Ping 消息已发送');
            return true;
        } catch (error) {
            console.error('❌ Ping 测试失败:', error);
            return false;
        }
    }

    handleWebSocketMessage(message) {
        console.log('收到 WebSocket 消息:', message);
        
        // 创建消息指纹用于去重
        const messageContent = message.content || message.prompt || message.error || '';
        const messageFingerprint = this.createMessageFingerprint(message);
        
        console.log('消息指纹:', messageFingerprint);
        console.log('已处理消息数量:', this.processedMessages.size);
        
        // 检查是否已处理过此消息
        if (this.processedMessages.has(messageFingerprint)) {
            console.log('❌ 消息已处理过，跳过:', messageFingerprint);
            return;
        }
        
        // 标记消息为已处理
        this.processedMessages.add(messageFingerprint);
        console.log('✅ 消息标记为已处理:', messageFingerprint);
        
        // 限制已处理消息集合的大小，避免内存泄漏
        if (this.processedMessages.size > 50) {
            // 保留最新的25条记录
            const messagesArray = Array.from(this.processedMessages);
            this.processedMessages.clear();
            messagesArray.slice(-25).forEach(msg => this.processedMessages.add(msg));
        }
        
        if (message.type === 'response') {
            // 处理智能体回复
            this.addMessage(message.content, 'agent');
            this.parseSpecialContent(message.content);
            this.saveToHistory(this.lastUserMessage, message.content);
            
            // 检查是否需要用户输入
            if (this.checkForUserInputRequest(message.content)) {
                this.handleUserInputRequest(message.content);
                return; // 不停止加载状态，等待用户输入
            }
        } else if (message.type === 'userInputRequired') {
            // 处理用户输入请求
            this.handleUserInputRequest(message.prompt);
            return; // 不停止加载状态，等待用户输入
        } else if (message.type === 'error') {
            // 处理错误
            this.addMessage('抱歉，处理您的请求时出现了错误：' + message.error, 'agent');
        }
        
        // 只有在正常响应或错误时才停止加载状态
        this.setLoading(false);
    }

    /**
     * 创建消息指纹用于去重
     */
    createMessageFingerprint(message) {
        const content = message.content || message.prompt || message.error || '';
        const type = message.type || 'unknown';
        const sessionId = message.sessionId || this.currentSessionId;
        
        // 使用更简单的指纹生成方式
        const fingerprint = `${type}_${sessionId}_${content.length}_${content.substring(0, 50)}`;
        return fingerprint;
    }

    handleWebSocketError(error) {
        console.error('WebSocket 错误:', error);
        this.addMessage('连接出现问题，请稍后重试', 'agent');
        this.setLoading(false);
    }

    async sendMessage() {
        const messageInput = document.getElementById('messageInput');
        const message = messageInput.value.trim();
        
        if (!message || this.isLoading || !this.connected) {
            if (!this.connected) {
                this.addMessage('WebSocket 连接未建立，请稍后重试', 'agent');
            }
            return;
        }

        // 添加用户消息到界面
        this.addMessage(message, 'user');
        this.lastUserMessage = message; // 保存用户消息用于历史记录
        messageInput.value = '';
        messageInput.style.height = 'auto';

        // 显示加载状态
        this.setLoading(true);

        try {
            // 判断是用户输入还是新消息
            if (this.waitingForUserInput) {
                // 用户输入模式：发送到 /traveling/human/input
                this.sendWebSocketMessage('/app/traveling/human/input', {
                    request: {
                        sessionId: this.currentSessionId,
                        chat: message
                    }
                });
                this.waitingForUserInput = false;
                this.userInputPrompt = '';
                
                // 重置输入框样式
                const inputContainer = document.querySelector('.input-container');
                inputContainer.classList.remove('waiting-for-input');
                messageInput.placeholder = '请描述您的旅游需求，例如：我想去日本旅游7天，预算1万元...';
            } else {
                // 新消息模式：发送到 /traveling/chat
                this.sendWebSocketMessage('/app/traveling/chat', {
                    request: {
                        sessionId: this.currentSessionId,
                        chat: message
                    }
                });
            }
            
        } catch (error) {
            console.error('发送消息失败:', error);
            this.addMessage('抱歉，处理您的请求时出现了错误。请稍后重试。', 'agent');
            this.setLoading(false);
        }
    }

    sendWebSocketMessage(destination, payload) {
        if (!this.stompClient || !this.connected) {
            throw new Error('WebSocket 未连接');
        }
        
        this.stompClient.send(destination, {}, JSON.stringify(payload));
    }


    checkForUserInputRequest(response) {
        // 检查响应是否包含用户输入请求的标识
        // 这里可以根据后端返回的特殊标识来判断
        // 例如：包含 "userInputRequired" 或特定的提示文本
        return response.includes('需要您提供') || 
               response.includes('请提供') || 
               response.includes('请输入') ||
               response.includes('请选择') ||
               response.includes('请确认');
    }

    handleUserInputRequest(response) {
        console.log('处理用户输入请求:', response);
        
        // 设置用户输入状态
        this.waitingForUserInput = true;
        this.userInputPrompt = response;
        
        // 停止加载状态
        this.setLoading(false);
        
        // 更新输入框提示和样式
        const messageInput = document.getElementById('messageInput');
        const inputContainer = document.querySelector('.input-container');
        
        messageInput.placeholder = '请根据上述要求提供信息...';
        inputContainer.classList.add('waiting-for-input');
        messageInput.focus();
        
        // 显示用户输入提示
        this.showUserInputPrompt(response);
        
        // 显示通知
        this.showNotification('智能体需要您的输入才能继续', 'info');
    }

    showUserInputPrompt(prompt) {
        // 检查是否已经显示过相同的用户输入提示，避免重复
        const messagesContainer = document.getElementById('messagesContainer');
        const lastMessage = messagesContainer.lastElementChild;
        
        if (lastMessage && lastMessage.classList.contains('agent-message')) {
            const lastMessageText = lastMessage.querySelector('.message-text');
            if (lastMessageText && lastMessageText.textContent.includes(prompt.substring(0, 50))) {
                console.log('用户输入提示已存在，跳过重复显示');
                return;
            }
        }
        
        // 直接作为普通智能体消息显示
        this.addMessage(prompt, 'agent');
    }

    addMessage(content, sender) {
        console.log(`💬 添加消息 - 发送者: ${sender}, 内容长度: ${content.length}`);
        console.log(`💬 消息内容预览: ${content.substring(0, 100)}...`);
        
        // 检查是否与最后一条消息重复
        const messagesContainer = document.getElementById('messagesContainer');
        const lastMessage = messagesContainer.lastElementChild;
        
        if (lastMessage && lastMessage.classList.contains(`${sender}-message`)) {
            const lastMessageText = lastMessage.querySelector('.message-text');
            if (lastMessageText) {
                const lastContent = lastMessageText.textContent || lastMessageText.innerText || '';
                const currentContent = content.replace(/\s+/g, ' ').trim();
                const lastContentNormalized = lastContent.replace(/\s+/g, ' ').trim();
                
                if (currentContent === lastContentNormalized || 
                    (currentContent.length > 20 && lastContentNormalized.includes(currentContent.substring(0, 20)))) {
                    console.log('❌ 检测到重复消息，跳过添加');
                    return;
                }
            }
        }
        
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}-message`;
        
        const now = new Date();
        const timeString = now.toLocaleTimeString('zh-CN', { 
            hour: '2-digit', 
            minute: '2-digit' 
        });

        messageDiv.innerHTML = `
            <div class="message-avatar">
                <i class="fas fa-${sender === 'user' ? 'user' : 'robot'}"></i>
            </div>
            <div class="message-content">
                <div class="message-header">
                    <span class="sender-name">${sender === 'user' ? '您' : '旅游智能体'}</span>
                    <span class="message-time">${timeString}</span>
                </div>
                <div class="message-text">
                    ${this.formatMessage(content)}
                </div>
            </div>
        `;

        messagesContainer.appendChild(messageDiv);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;

        // 添加动画效果
        messageDiv.classList.add('fade-in');
        
        // 如果是智能体消息，添加旅游可视化效果
        if (sender === 'agent' && this.travelVisualization) {
            this.travelVisualization.enhanceMessageWithIcons(messageDiv);
            this.travelVisualization.animateMessage(messageDiv);
        }
        
        console.log(`💬 消息已添加到界面`);
    }

    formatMessage(content) {
        if (!content) return '';
        
        // 去除前导和尾随空格
        let formatted = content.trim();
        
        // 检测是否包含表格格式（更精确的检测）
        const hasTable = this.detectTableFormat(formatted);
        
        if (hasTable) {
            // 处理表格格式：保持原始换行和空行
            formatted = this.formatTableContent(formatted);
        } else {
            // 处理普通Markdown格式
            formatted = this.formatRegularContent(formatted);
        }
        
        return formatted;
    }

    detectTableFormat(content) {
        // 检测是否包含表格格式
        // 1. 包含管道符分隔的列
        // 2. 包含分隔符行（如 |------|------|）
        // 3. 包含表格头部和数据的组合
        const hasPipes = content.includes('|');
        const hasSeparators = /^\s*\|[\s\-|]+\|\s*$/m.test(content);
        const hasTableStructure = /^\s*\|.*\|.*\|\s*$/m.test(content);
        
        return hasPipes && (hasSeparators || hasTableStructure);
    }

    formatTableContent(content) {
        // 保持表格的原始格式，包括空行和分隔符
        let formatted = content
            // 处理Markdown格式
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            .replace(/`(.*?)`/g, '<code>$1</code>')
            .replace(/^### (.*$)/gm, '<h3>$1</h3>')
            .replace(/^## (.*$)/gm, '<h2>$1</h2>')
            .replace(/^# (.*$)/gm, '<h1>$1</h1>')
            .replace(/^\* (.*$)/gm, '<li>$1</li>')
            .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
        
        // 对于表格内容，保持原始换行符，但将换行符转换为<br>
        // 同时保持空行（连续换行符）
        formatted = formatted
            .replace(/\n\n+/g, '<br><br>')  // 保持空行
            .replace(/\n/g, '<br>');  // 单个换行转为br
        
        // 确保空行在HTML中正确显示
        formatted = formatted.replace(/<br><br>/g, '<br><br>');
        
        // 包装在pre标签中以保持格式
        formatted = '<div class="table-content">' + formatted + '</div>';
        
        return formatted;
    }

    formatRegularContent(content) {
        // 处理普通内容的Markdown格式化
        let formatted = content
            // 先处理Windows风格的换行符
            .replace(/\r\n/g, '\n')
            .replace(/\r/g, '\n')
            // 处理Markdown格式
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            .replace(/`(.*?)`/g, '<code>$1</code>')
            .replace(/^### (.*$)/gm, '<h3>$1</h3>')
            .replace(/^## (.*$)/gm, '<h2>$1</h2>')
            .replace(/^# (.*$)/gm, '<h1>$1</h1>')
            .replace(/^\* (.*$)/gm, '<li>$1</li>')
            .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
        
        // 处理换行符：将连续两个换行符转为段落，单个换行符转为br
        formatted = formatted
            .replace(/\n\n+/g, '</p><p>')  // 连续换行转为段落
            .replace(/\n/g, '<br>');  // 单个换行转为br
        
        // 包装在段落中
        if (!formatted.startsWith('<')) {
            formatted = '<p>' + formatted + '</p>';
        }
        
        return formatted;
    }

    setLoading(loading) {
        this.isLoading = loading;
        const loadingOverlay = document.getElementById('loadingOverlay');
        const sendBtn = document.getElementById('sendBtn');
        
        if (loading) {
            loadingOverlay.classList.add('show');
            sendBtn.disabled = true;
        } else {
            loadingOverlay.classList.remove('show');
            sendBtn.disabled = false;
        }
    }

    async checkAgentStatus() {
        try {
            const response = await fetch('/api/traveling/status');
            const statusText = await response.text();
            
            const statusIndicator = document.getElementById('agentStatus');
            const statusDot = statusIndicator.querySelector('.status-dot');
            const statusText_ = statusIndicator.querySelector('.status-text');
            
            if (response.ok) {
                statusDot.className = 'status-dot';
                statusText_.textContent = '在线';
            } else {
                statusDot.className = 'status-dot offline';
                statusText_.textContent = '离线';
            }
        } catch (error) {
            console.error('检查智能体状态失败:', error);
            const statusIndicator = document.getElementById('agentStatus');
            const statusDot = statusIndicator.querySelector('.status-dot');
            const statusText_ = statusIndicator.querySelector('.status-text');
            
            statusDot.className = 'status-dot offline';
            statusText_.textContent = '连接失败';
        }
    }

    startNewChat() {
        // 保存当前对话到历史记录（如果有的话）
        this.saveCurrentChatToHistory();
        
        this.currentSessionId = this.generateSessionId();
        this.messageHistory = [];
        this.waitingForUserInput = false; // 重置用户输入状态
        this.userInputPrompt = '';
        
        // 清理已处理消息集合，避免跨会话消息干扰
        this.processedMessages.clear();
        
        // 清空消息容器并重新创建欢迎消息
        const messagesContainer = document.getElementById('messagesContainer');
        messagesContainer.innerHTML = '';
        
        // 重新创建欢迎消息
        this.createWelcomeMessage();
        
        // 重置输入框
        const messageInput = document.getElementById('messageInput');
        messageInput.placeholder = '请描述您的旅游需求，例如：我想去日本旅游7天，预算1万元...';
        
        // 清空当前会话的临时存储
        this.clearCurrentSessionCache();
        
        // 更新聊天历史
        this.loadChatHistory();
        
        // 显示提示
        this.showNotification('新对话已开始', 'success');
    }

    showSettingsModal() {
        document.getElementById('settingsModal').classList.add('show');
        
        // 填充当前设置
        document.getElementById('modelSelect').value = this.settings.model;
        document.getElementById('temperatureSlider').value = this.settings.temperature;
        document.getElementById('temperatureValue').textContent = this.settings.temperature;
        document.getElementById('maxTokensInput').value = this.settings.maxTokens;
    }

    hideSettingsModal() {
        document.getElementById('settingsModal').classList.remove('show');
    }

    saveSettings() {
        this.settings.model = document.getElementById('modelSelect').value;
        this.settings.temperature = parseFloat(document.getElementById('temperatureSlider').value);
        this.settings.maxTokens = parseInt(document.getElementById('maxTokensInput').value);
        
        localStorage.setItem('travelingAgentSettings', JSON.stringify(this.settings));
        this.hideSettingsModal();
        this.showNotification('设置已保存', 'success');
    }

    loadSettings() {
        const saved = localStorage.getItem('travelingAgentSettings');
        if (saved) {
            this.settings = { ...this.settings, ...JSON.parse(saved) };
        }
    }

    showUploadModal() {
        document.getElementById('uploadModal').classList.add('show');
    }

    hideUploadModal() {
        document.getElementById('uploadModal').classList.remove('show');
        // 清空文件列表
        document.getElementById('uploadedFiles').innerHTML = '';
        document.getElementById('fileInput').value = '';
    }

    handleFileDrop(files) {
        this.handleFileSelect(files);
    }

    handleFileSelect(files) {
        const uploadedFiles = document.getElementById('uploadedFiles');
        uploadedFiles.innerHTML = '';
        
        Array.from(files).forEach(file => {
            if (this.validateFile(file)) {
                const fileItem = document.createElement('div');
                fileItem.className = 'file-item';
                fileItem.innerHTML = `
                    <div class="file-info">
                        <i class="fas fa-file"></i>
                        <span class="file-name">${file.name}</span>
                        <span class="file-size">${this.formatFileSize(file.size)}</span>
                    </div>
                    <button class="remove-file-btn" onclick="this.parentElement.remove()">
                        <i class="fas fa-times"></i>
                    </button>
                `;
                uploadedFiles.appendChild(fileItem);
            }
        });
    }

    validateFile(file) {
        const allowedTypes = ['application/pdf', 'application/msword', 
                            'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 
                            'text/plain'];
        const maxSize = 10 * 1024 * 1024; // 10MB
        
        if (!allowedTypes.includes(file.type)) {
            this.showNotification('不支持的文件类型', 'error');
            return false;
        }
        
        if (file.size > maxSize) {
            this.showNotification('文件大小超过10MB限制', 'error');
            return false;
        }
        
        return true;
    }

    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    async uploadFiles() {
        const fileInput = document.getElementById('fileInput');
        const files = fileInput.files;
        
        if (files.length === 0) {
            this.showNotification('请选择要上传的文件', 'warning');
            return;
        }

        const formData = new FormData();
        Array.from(files).forEach(file => {
            formData.append('files', file);
        });

        try {
            this.setLoading(true);
            const response = await fetch('/api/traveling/upload-travel-plan', {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                const result = await response.text();
                this.addMessage(`文件上传成功！\n\n${result}`, 'agent');
                this.hideUploadModal();
                this.showNotification('文件上传成功', 'success');
            } else {
                throw new Error('上传失败');
            }
        } catch (error) {
            console.error('文件上传失败:', error);
            this.showNotification('文件上传失败', 'error');
        } finally {
            this.setLoading(false);
        }
    }

    saveToHistory(userMessage, agentResponse) {
        this.messageHistory.push({
            timestamp: Date.now(),
            user: userMessage,
            agent: agentResponse,
            sessionId: this.currentSessionId
        });
        
        // 限制历史记录数量，避免localStorage溢出
        this.limitHistorySize();
        
        // 保存到localStorage
        this.saveHistoryToStorage();
    }
    
    /**
     * 保存当前对话到历史记录
     */
    saveCurrentChatToHistory() {
        const messagesContainer = document.getElementById('messagesContainer');
        const messages = messagesContainer.querySelectorAll('.message');
        
        if (messages.length > 1) { // 除了欢迎消息
            let currentUserMessage = '';
            let currentAgentMessage = '';
            
            messages.forEach(message => {
                if (message.classList.contains('user-message')) {
                    const messageText = message.querySelector('.message-text');
                    if (messageText) {
                        currentUserMessage = messageText.textContent.trim();
                    }
                } else if (message.classList.contains('agent-message')) {
                    const messageText = message.querySelector('.message-text');
                    if (messageText && !messageText.textContent.includes('您好！我是您的专属旅游智能体')) {
                        currentAgentMessage = messageText.textContent.trim();
                    }
                }
            });
            
            if (currentUserMessage && currentAgentMessage) {
                this.messageHistory.push({
                    timestamp: Date.now(),
                    user: currentUserMessage,
                    agent: currentAgentMessage,
                    sessionId: this.currentSessionId
                });
                
                this.limitHistorySize();
                this.saveHistoryToStorage();
            }
        }
    }
    
    /**
     * 限制历史记录大小
     */
    limitHistorySize() {
        const MAX_HISTORY_SIZE = 50; // 最多保存50条历史记录
        
        if (this.messageHistory.length > MAX_HISTORY_SIZE) {
            // 按时间排序，保留最新的记录
            this.messageHistory.sort((a, b) => b.timestamp - a.timestamp);
            this.messageHistory = this.messageHistory.slice(0, MAX_HISTORY_SIZE);
        }
    }
    
    /**
     * 保存历史记录到localStorage
     */
    saveHistoryToStorage() {
        try {
            localStorage.setItem('travelingAgentHistory', JSON.stringify(this.messageHistory));
        } catch (error) {
            console.error('保存历史记录失败:', error);
            // 如果localStorage满了，清理一些旧数据
            this.cleanupOldHistory();
            try {
                localStorage.setItem('travelingAgentHistory', JSON.stringify(this.messageHistory));
            } catch (retryError) {
                console.error('重试保存历史记录失败:', retryError);
                this.showNotification('历史记录保存失败，请清理浏览器缓存', 'error');
            }
        }
    }
    
    /**
     * 清理过期历史记录
     */
    cleanupExpiredHistory() {
        const EXPIRY_DAYS = 30; // 30天过期
        const expiryTime = Date.now() - (EXPIRY_DAYS * 24 * 60 * 60 * 1000);
        
        const originalLength = this.messageHistory.length;
        this.messageHistory = this.messageHistory.filter(chat => chat.timestamp > expiryTime);
        
        if (this.messageHistory.length !== originalLength) {
            this.saveHistoryToStorage();
        }
    }
    
    /**
     * 清理旧历史记录（当localStorage满时）
     */
    cleanupOldHistory() {
        // 保留最新的20条记录
        this.messageHistory.sort((a, b) => b.timestamp - a.timestamp);
        this.messageHistory = this.messageHistory.slice(0, 20);
    }
    
    /**
     * 清空当前会话缓存
     */
    clearCurrentSessionCache() {
        // 清理当前会话相关的临时数据
        const sessionKey = `traveling_session_${this.currentSessionId}`;
        localStorage.removeItem(sessionKey);
    }
    
    /**
     * 删除单个历史记录项
     */
    deleteHistoryItem(index) {
        if (index >= 0 && index < this.messageHistory.length) {
            this.messageHistory.splice(index, 1);
            this.saveHistoryToStorage();
            this.loadChatHistory(); // 重新加载显示
            this.showNotification('对话记录已删除', 'success');
        }
    }
    
    /**
     * 清空所有历史记录
     */
    clearAllHistory() {
        if (confirm('确定要清空所有对话历史吗？此操作不可恢复。')) {
            this.messageHistory = [];
            localStorage.removeItem('travelingAgentHistory');
            this.loadChatHistory();
            this.showNotification('所有对话历史已清空', 'success');
        }
    }
    
    /**
     * 创建欢迎消息
     */
    createWelcomeMessage() {
        const messagesContainer = document.getElementById('messagesContainer');
        const welcomeMessageDiv = document.createElement('div');
        welcomeMessageDiv.className = 'message agent-message';
        welcomeMessageDiv.innerHTML = `
            <div class="message-avatar">
                <i class="fas fa-robot"></i>
            </div>
            <div class="message-content">
                <div class="message-header">
                    <span class="sender-name">旅游智能体</span>
                    <span class="message-time">刚刚</span>
                </div>
                <div class="message-text">
                    <p>👋 您好！我是您的专属旅游智能体，我可以帮助您：</p>
                    <ul>
                        <li>🔍 搜索目的地信息和景点详情</li>
                        <li>📅 制定个性化旅游行程</li>
                        <li>🏨 预订酒店、机票等服务</li>
                        <li>🗺️ 提供出行建议和实时监控</li>
                    </ul>
                    <p>请告诉我您的旅游需求，让我为您规划一次完美的旅程！</p>
                </div>
            </div>
        `;
        
        messagesContainer.appendChild(welcomeMessageDiv);
    }

    loadChatHistory() {
        const historyContainer = document.getElementById('chatHistory');
        const saved = localStorage.getItem('travelingAgentHistory');
        
        if (saved) {
            try {
                this.messageHistory = JSON.parse(saved);
            } catch (error) {
                console.error('解析历史记录失败:', error);
                this.messageHistory = [];
                localStorage.removeItem('travelingAgentHistory');
            }
        }
        
        // 清理过期数据
        this.cleanupExpiredHistory();
        
        // 显示最近的对话（按时间倒序，最新的在前）
        historyContainer.innerHTML = '';
        const recentChats = this.messageHistory
            .sort((a, b) => b.timestamp - a.timestamp)
            .slice(0, 10);
        
        recentChats.forEach((chat, index) => {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';
            historyItem.innerHTML = `
                <div class="history-preview">
                    <div class="history-user-message">${this.truncateText(chat.user, 30)}</div>
                    <div class="history-time">${new Date(chat.timestamp).toLocaleString('zh-CN')}</div>
                </div>
                <div class="history-actions">
                    <button class="delete-history-btn" onclick="event.stopPropagation(); window.travelingAgentApp.deleteHistoryItem(${index})" title="删除此对话">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            `;
            
            historyItem.addEventListener('click', () => {
                this.loadChatFromHistory(chat);
            });
            
            historyContainer.appendChild(historyItem);
        });
    }

    loadChatFromHistory(chat) {
        // 清空当前消息
        const messagesContainer = document.getElementById('messagesContainer');
        messagesContainer.innerHTML = '';
        
        // 重新添加历史消息
        this.addMessage(chat.user, 'user');
        this.addMessage(chat.agent, 'agent');
    }

    truncateText(text, maxLength) {
        return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
    }

    exportChat() {
        if (this.messageHistory.length === 0) {
            this.showNotification('没有对话记录可导出', 'warning');
            return;
        }

        let exportText = '旅游智能体对话记录\n';
        exportText += '='.repeat(50) + '\n\n';
        
        this.messageHistory.forEach((chat, index) => {
            exportText += `对话 ${index + 1} (${new Date(chat.timestamp).toLocaleString('zh-CN')})\n`;
            exportText += `用户: ${chat.user}\n`;
            exportText += `智能体: ${chat.agent}\n`;
            exportText += '-'.repeat(30) + '\n\n';
        });

        const blob = new Blob([exportText], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `旅游智能体对话记录_${new Date().toISOString().split('T')[0]}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        this.showNotification('对话记录已导出', 'success');
    }

    showNotification(message, type = 'info') {
        // 创建通知元素
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.innerHTML = `
            <div class="notification-content">
                <i class="fas fa-${this.getNotificationIcon(type)}"></i>
                <span>${message}</span>
            </div>
        `;
        
        // 添加样式
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: var(--bg-primary);
            border: 1px solid var(--border-color);
            border-radius: var(--border-radius);
            padding: var(--spacing-md);
            box-shadow: var(--shadow-lg);
            z-index: 1001;
            animation: slideInRight 0.3s ease-out;
        `;
        
        // 根据类型设置颜色
        const colors = {
            success: 'var(--success-color)',
            error: 'var(--error-color)',
            warning: 'var(--warning-color)',
            info: 'var(--primary-color)'
        };
        
        notification.style.borderLeftColor = colors[type] || colors.info;
        
        document.body.appendChild(notification);
        
        // 3秒后自动移除
        setTimeout(() => {
            notification.style.animation = 'slideOutRight 0.3s ease-in';
            setTimeout(() => {
                if (notification.parentNode) {
                    notification.parentNode.removeChild(notification);
                }
            }, 300);
        }, 3000);
    }

    getNotificationIcon(type) {
        const icons = {
            success: 'check-circle',
            error: 'exclamation-circle',
            warning: 'exclamation-triangle',
            info: 'info-circle'
        };
        return icons[type] || icons.info;
    }

    initTravelVisualization() {
        if (typeof TravelVisualization !== 'undefined') {
            this.travelVisualization = new TravelVisualization();
        }
    }

    showTravelProgress(step, total) {
        if (this.travelVisualization) {
            this.travelVisualization.showTravelProgress(step, total);
        }
    }

    showTravelMap(destinations) {
        if (this.travelVisualization) {
            const mapWidget = this.travelVisualization.showTravelMap(destinations);
            const messagesContainer = document.getElementById('messagesContainer');
            messagesContainer.appendChild(mapWidget);
        }
    }

    showWeatherWidget(location, weather) {
        if (this.travelVisualization) {
            const weatherWidget = this.travelVisualization.showWeatherWidget(location, weather);
            const messagesContainer = document.getElementById('messagesContainer');
            messagesContainer.appendChild(weatherWidget);
        }
    }

    showBudgetBreakdown(budget) {
        if (this.travelVisualization) {
            const budgetWidget = this.travelVisualization.showBudgetBreakdown(budget);
            const messagesContainer = document.getElementById('messagesContainer');
            messagesContainer.appendChild(budgetWidget);
        }
    }

    showCelebration() {
        if (this.travelVisualization) {
            this.travelVisualization.showCelebration();
        }
    }

    // 测试换行符处理
    testLineBreakHandling() {
        const testContent = `第一行内容

第二行内容（前面有空行）

第三行内容

最后一行`;
        
        console.log('原始内容:', JSON.stringify(testContent));
        console.log('格式化后:', this.formatMessage(testContent));
        
        // 添加测试消息到界面
        this.addMessage(testContent, 'agent');
    }

    // 解析智能体回复中的特殊内容
    parseSpecialContent(content) {
        // 检查是否包含旅游地图信息
        const mapMatch = content.match(/地图信息[：:](.*?)(?=\n\n|\n$|$)/s);
        if (mapMatch) {
            try {
                const destinations = JSON.parse(mapMatch[1]);
                this.showTravelMap(destinations);
            } catch (e) {
                console.log('无法解析地图信息');
            }
        }

        // 检查是否包含天气信息
        const weatherMatch = content.match(/天气信息[：:](.*?)(?=\n\n|\n$|$)/s);
        if (weatherMatch) {
            try {
                const weatherData = JSON.parse(weatherMatch[1]);
                this.showWeatherWidget(weatherData.location, weatherData.weather);
            } catch (e) {
                console.log('无法解析天气信息');
            }
        }

        // 检查是否包含预算信息
        const budgetMatch = content.match(/预算明细[：:](.*?)(?=\n\n|\n$|$)/s);
        if (budgetMatch) {
            try {
                const budgetData = JSON.parse(budgetMatch[1]);
                this.showBudgetBreakdown(budgetData);
            } catch (e) {
                console.log('无法解析预算信息');
            }
        }

        // 检查是否包含进度信息
        const progressMatch = content.match(/进度[：:](.*?)(?=\n\n|\n$|$)/s);
        if (progressMatch) {
            try {
                const progressData = JSON.parse(progressMatch[1]);
                this.showTravelProgress(progressData.step, progressData.total);
            } catch (e) {
                console.log('无法解析进度信息');
            }
        }

        // 检查是否包含完成信息
        if (content.includes('规划完成') || content.includes('任务完成')) {
            setTimeout(() => this.showCelebration(), 1000);
        }
    }
}

// 添加通知动画样式
const style = document.createElement('style');
style.textContent = `
    @keyframes slideInRight {
        from {
            opacity: 0;
            transform: translateX(100%);
        }
        to {
            opacity: 1;
            transform: translateX(0);
        }
    }
    
    @keyframes slideOutRight {
        from {
            opacity: 1;
            transform: translateX(0);
        }
        to {
            opacity: 0;
            transform: translateX(100%);
        }
    }
    
    .notification-content {
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);
    }
    
    .notification-content i {
        color: var(--primary-color);
    }
    
    .file-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--spacing-sm);
        background: var(--bg-tertiary);
        border-radius: var(--border-radius-sm);
        margin-bottom: var(--spacing-sm);
    }
    
    .file-info {
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);
    }
    
    .file-name {
        font-weight: 500;
    }
    
    .file-size {
        color: var(--text-muted);
        font-size: var(--font-size-xs);
    }
    
    .remove-file-btn {
        background: none;
        border: none;
        color: var(--text-muted);
        cursor: pointer;
        padding: var(--spacing-xs);
        border-radius: var(--border-radius-sm);
        transition: all 0.2s ease;
    }
    
    .remove-file-btn:hover {
        background: var(--error-color);
        color: var(--text-white);
    }
    
    .history-preview {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-xs);
    }
    
    .history-user-message {
        font-weight: 500;
        color: var(--text-white);
    }
    
    .history-time {
        font-size: var(--font-size-xs);
        color: var(--text-muted);
    }
    
    
    /* 等待用户输入时的输入框样式 - 简洁版本 */
    .input-container.waiting-for-input {
        border: 1px solid var(--primary-color);
        background: rgba(59, 130, 246, 0.02);
    }
`;
document.head.appendChild(style);

// 初始化应用
document.addEventListener('DOMContentLoaded', () => {
    window.travelingAgentApp = new TravelingAgentApp();
});

// 添加文件上传按钮事件
document.addEventListener('DOMContentLoaded', () => {
    const uploadFilesBtn = document.getElementById('uploadFiles');
    if (uploadFilesBtn) {
        uploadFilesBtn.addEventListener('click', () => {
            window.travelingAgentApp.uploadFiles();
        });
    }
});

// 添加测试函数到全局作用域
window.testLineBreakHandling = function() {
    if (window.travelingAgentApp) {
        console.log('🧪 测试换行符处理...');
        
        const testContent = `这是第一行
这是第二行

这是空行后的内容

最后一行`;

        console.log('原始内容:');
        console.log(testContent);
        
        const formatted = window.travelingAgentApp.formatMessage(testContent);
        console.log('格式化后:');
        console.log(formatted);
        
        // 添加到消息中测试
        window.travelingAgentApp.addMessage(testContent, 'agent');
    }
};

window.testWindowsLineBreaks = function() {
    if (window.travelingAgentApp) {
        console.log('🧪 测试Windows风格换行符处理...');
        
        const testContent = `Windows风格换行符测试:\r\n第一行\r\n第二行\r\n\r\n空行后的内容\r\n\r\n最后一行`;

        console.log('原始内容:');
        console.log(testContent);
        
        const formatted = window.travelingAgentApp.formatMessage(testContent);
        console.log('格式化后:');
        console.log(formatted);
        
        // 添加到消息中测试
        window.travelingAgentApp.addMessage(testContent, 'agent');
    }
};

window.testDuplicateMessage = function() {
    if (window.travelingAgentApp) {
        console.log('🧪 测试重复消息检测...');
        
        const testContent = '这是一条测试消息，用于检测重复消息功能';
        
        // 添加第一条消息
        window.travelingAgentApp.addMessage(testContent, 'agent');
        
        // 立即添加相同的消息（应该被检测为重复）
        setTimeout(() => {
            window.travelingAgentApp.addMessage(testContent, 'agent');
        }, 100);
        
        // 添加不同的消息（应该正常显示）
        setTimeout(() => {
            window.travelingAgentApp.addMessage('这是另一条不同的消息', 'agent');
        }, 200);
    }
};
