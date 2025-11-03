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
            // 智能检测端口：如果页面是从服务器加载的，使用当前端口；否则使用配置的端口
            const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
            const hostname = window.location.hostname || 'localhost';
            
            // 如果是 file:// 协议或者没有端口，使用默认端口 8085
            let port;
            if (window.location.protocol === 'file:' || !window.location.port) {
                port = '8085'; // 使用应用配置的端口
            } else {
                port = window.location.port; // 使用当前页面端口
            }
            
            const wsUrl = `${protocol}//${hostname}:${port}/ws`;
            
            console.log('=== WebSocket 连接信息 ===');
            console.log('WebSocket连接URL:', wsUrl);
            console.log('当前页面协议:', window.location.protocol);
            console.log('当前页面主机:', window.location.hostname);
            console.log('当前页面端口:', window.location.port);
            console.log('使用的端口:', port);
            console.log('========================');
            
            // 检查 SockJS 是否已加载
            if (typeof SockJS === 'undefined') {
                console.error('❌ SockJS 未加载！请检查 CDN 连接');
                this.updateConnectionStatus(false);
                return;
            }
            
            // 检查 Stomp 是否已加载
            if (typeof Stomp === 'undefined') {
                console.error('❌ Stomp 未加载！请检查 CDN 连接');
                this.updateConnectionStatus(false);
                return;
            }
            
            console.log('✅ SockJS 和 Stomp 库已加载');
            const socket = new SockJS(wsUrl);
            this.stompClient = Stomp.over(socket);
            
            // 添加 socket 事件监听，用于诊断
            socket.onopen = function() {
                console.log('✅ SockJS socket 已打开');
            };
            
            socket.onmessage = function(e) {
                console.log('📨 SockJS 收到原始消息:', e.data);
            };
            
            socket.onclose = function(e) {
                console.log('❌ SockJS socket 已关闭:', e.code, e.reason);
                console.log('关闭原因:', e.wasClean ? '正常关闭' : '异常关闭');
            };
            
            socket.onerror = function(error) {
                console.error('❌ SockJS socket 错误:', error);
                console.error('错误详情:', error.type, error.target);
            };
            
            // 启用调试日志（临时）
            this.stompClient.debug = function(str) {
                console.log('STOMP Debug:', str);
            };
            
            // 设置心跳
            this.stompClient.heartbeat.outgoing = 20000; // 客户端发送心跳间隔 20秒
            this.stompClient.heartbeat.incoming = 0;     // 不接收服务器心跳
            
            // 连接 WebSocket
            this.connectionCounter++;
            console.log(`🔗 第${this.connectionCounter}次WebSocket连接尝试`);
            console.log('⏳ 正在连接，请稍候...');
            
            // 设置连接超时（10秒）
            const connectTimeout = setTimeout(() => {
                if (!this.connected) {
                    console.error('⏱️ 连接超时（10秒），可能的原因：');
                    console.error('  1. 后端服务未启动');
                    console.error('  2. WebSocket端点配置错误');
                    console.error('  3. 端口被防火墙阻止');
                    console.error('  4. 跨域问题');
                    this.connected = false;
                    this.updateConnectionStatus(false);
                    
                    // 尝试重新连接
                    console.log('🔄 将尝试重新连接...');
                    this.scheduleReconnect();
                }
            }, 10000);
            
            // STOMP connect 方法签名: connect(headers, connectCallback, errorCallback)
            this.stompClient.connect({}, (frame) => {
                clearTimeout(connectTimeout);
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
                clearTimeout(connectTimeout);
                console.error('❌ WebSocket 连接失败:');
                console.error('错误对象:', error);
                console.error('错误类型:', typeof error);
                console.error('错误信息:', error?.toString ? error.toString() : JSON.stringify(error));
                
                // 打印详细的错误信息
                if (error.headers) {
                    console.error('错误头信息:', error.headers);
                }
                if (error.body) {
                    console.error('错误体:', error.body);
                }
                
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
            statusText.textContent = '连接失败 - 点击WiFi图标诊断';
            console.log('❌ WebSocket 连接状态更新为：连接失败');
        }
    }

    // 添加WebSocket连接测试方法
    async testWebSocketConnection() {
        console.log('🧪 ========== 开始诊断 WebSocket 连接 ==========');
        
        // 1. 测试后端服务是否可访问
        const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
        const hostname = window.location.hostname || 'localhost';
        const port = window.location.port || '8085';
        const baseUrl = `${protocol}//${hostname}:${port}`;
        const healthUrl = `${baseUrl}/actuator/health`;
        
        console.log('📍 测试目标:', baseUrl);
        console.log('🔍 健康检查URL:', healthUrl);
        
        try {
            console.log('正在检查后端服务...');
            const response = await fetch(healthUrl, {
                method: 'GET',
                cache: 'no-cache',
                headers: {
                    'Accept': 'application/json'
                }
            });
            
            if (response.ok) {
                const data = await response.json();
                console.log('✅ 后端服务正常运行');
                console.log('健康状态:', data);
            } else {
                console.error('❌ 后端服务响应异常:', response.status, response.statusText);
                alert(`后端服务响应异常 (${response.status})\n请检查应用是否正常启动在端口 ${port}`);
                return false;
            }
        } catch (error) {
            console.error('❌ 无法连接到后端服务:', error.message);
            alert(`无法连接到后端服务！\n\n` + 
                  `请检查:\n` + 
                  `1. 应用是否已启动\n` + 
                  `2. 端口是否正确: ${port}\n` + 
                  `3. URL: ${baseUrl}\n\n` +
                  `错误: ${error.message}`);
            return false;
        }
        
        // 2. 检查当前WebSocket状态
        console.log('\n📊 当前WebSocket状态:');
        console.log('  - 已连接:', this.connected);
        console.log('  - STOMP客户端存在:', !!this.stompClient);
        console.log('  - 重连次数:', this.reconnectAttempts);
        
        // 3. 如果未连接，尝试重新连接
        if (!this.connected) {
            console.log('\n🔄 尝试重新建立连接...');
            this.cleanupWebSocket();
            this.reconnectAttempts = 0; // 重置重连次数
            this.initWebSocket();
            
            // 等待3秒检查连接结果
            setTimeout(() => {
                if (this.connected) {
                    console.log('✅ 重连成功！');
                    alert('WebSocket 连接已恢复！');
                } else {
                    console.error('❌ 重连失败');
                    alert('WebSocket 重连失败\n请查看浏览器控制台了解详情');
                }
            }, 3000);
        } else {
            // 已连接，发送ping测试
            try {
                this.stompClient.send('/app/traveling/ping', {}, 'ping');
                console.log('✅ Ping 消息已发送');
                alert('WebSocket 连接正常！');
                return true;
            } catch (error) {
                console.error('❌ Ping 测试失败:', error);
                alert('WebSocket 连接异常\n正在尝试重新连接...');
                this.cleanupWebSocket();
                this.initWebSocket();
                return false;
            }
        }
        
        console.log('========== 诊断结束 ==========\n');
        return true;
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
            // 保留工具执行容器，只更新状态标签为"已完成"
            const thinkingContainer = document.querySelector('.agent-thinking-container');
            if (thinkingContainer) {
                const thinkingLabel = thinkingContainer.querySelector('.thinking-label');
                if (thinkingLabel) {
                    // 停止旋转动画，更新为"已完成"
                    thinkingLabel.innerHTML = '<i class="fas fa-check-circle" style="color: #10b981;"></i><span style="color: #10b981;">思考完成</span>';
                    
                    // 确保容器在更新后保持可见，不会向上移动覆盖其他内容
                    thinkingContainer.style.position = 'relative';
                    thinkingContainer.style.marginBottom = '16px';
                }
            }
            
            // 处理智能体回复
            this.addMessage(message.content, 'agent');
            this.parseSpecialContent(message.content);
            this.saveToHistory(this.lastUserMessage, message.content);
            
            // 确保滚动到最新消息
            const messagesContainer = document.getElementById('messagesContainer');
            if (messagesContainer) {
                messagesContainer.scrollTop = messagesContainer.scrollHeight;
            }
            
            // 检查是否需要用户输入
            if (this.checkForUserInputRequest(message.content)) {
                this.handleUserInputRequest(message.content);
                return; // 不停止加载状态，等待用户输入
            }
            
            // 保留思考容器和工具执行信息，不删除
            // 工具执行历史会一直保留在界面上，直到开始新对话
        } else if (message.type === 'toolExecution') {
            // 确保思考容器存在（通用设计）
            const messagesContainer = document.getElementById('messagesContainer');
            let thinkingContainer = messagesContainer.querySelector('.agent-thinking-container');
            if (!thinkingContainer) {
                console.log('⚠️ 思考容器不存在，创建新的');
                thinkingContainer = this.createThinkingContainer();
                messagesContainer.appendChild(thinkingContainer);
            }
            
            // 显示工具执行信息在思考容器中
            this.handleToolExecution(message.data);
            
            // 检查是否有工具正在执行
            const hasExecuting = message.data && message.data.toolExecutions && 
                                message.data.toolExecutions.some(tool => tool.executing);
            
            if (!hasExecuting) {
                // 工具执行完成，更新思考标签为"正在处理结果"
                const thinkingContainer = document.querySelector('.agent-thinking-container');
                if (thinkingContainer) {
                    const thinkingLabel = thinkingContainer.querySelector('.thinking-label');
                    if (thinkingLabel) {
                        thinkingLabel.innerHTML = '<i class="fas fa-spinner fa-spin"></i><span>正在处理工具执行结果...</span>';
                    }
                    // 确保滚动位置正确
                    const messagesContainer = document.getElementById('messagesContainer');
                    if (messagesContainer) {
                        messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    }
                }
            } else {
                // 工具开始执行，更新思考标签为"正在执行工具"
                const thinkingContainer = document.querySelector('.agent-thinking-container');
                if (thinkingContainer) {
                    const thinkingLabel = thinkingContainer.querySelector('.thinking-label');
                    if (thinkingLabel) {
                        thinkingLabel.innerHTML = '<i class="fas fa-spinner fa-spin"></i><span>正在执行工具...</span>';
                    }
                }
            }
            
            return; // 不停止加载状态，继续等待后续消息
        } else if (message.type === 'userInputRequired') {
            // 处理用户输入请求
            this.handleUserInputRequest(message.prompt);
            return; // 不停止加载状态，等待用户输入
        } else if (message.type === 'userInputFormRequired') {
            // 处理表单输入请求
            this.handleFormInputRequest(message);
            return; // 不停止加载状态，等待表单提交
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
        const timestamp = message.timestamp || Date.now();
        
        // 特殊处理 toolExecution 消息：使用批次ID和执行状态来区分
        if (type === 'toolExecution' && message.data) {
            const batchId = message.data.batchId || 'no-batch';
            const hasExecuting = message.data.toolExecutions && 
                                message.data.toolExecutions.some(tool => tool.executing);
            const executingState = hasExecuting ? 'executing' : 'completed';
            const fingerprint = `${type}_${sessionId}_${batchId}_${executingState}`;
            return fingerprint;
        }
        
        // 对于 response 类型的消息，使用完整内容的哈希值来避免误判重复
        if (type === 'response') {
            // 使用完整内容生成哈希值（简单的字符串哈希）
            let hash = 0;
            for (let i = 0; i < content.length; i++) {
                const char = content.charCodeAt(i);
                hash = ((hash << 5) - hash) + char;
                hash = hash & hash; // Convert to 32bit integer
            }
            const fingerprint = `${type}_${sessionId}_${timestamp}_${hash}`;
            return fingerprint;
        }
        
        // 其他类型的消息使用原有逻辑
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
        
        // 检查输入框是否被禁用（例如表单填写期间）
        if (messageInput.disabled) {
            console.log('输入框已禁用，无法发送消息');
            return;
        }
        
        const message = messageInput.value.trim();
        
        if (!message || this.isLoading) {
            return;
        }
        
        // 检查WebSocket连接状态
        if (!this.connected) {
            console.error('❌ WebSocket 未连接，无法发送消息');
            alert('WebSocket 连接未建立！\n\n请点击左侧WiFi图标进行诊断，或等待连接自动恢复。');
            // 尝试重新连接
            if (this.reconnectAttempts < this.maxReconnectAttempts) {
                console.log('🔄 自动尝试重新连接...');
                this.initWebSocket();
            }
            return;
        }

        // 添加用户消息到界面
        this.addMessage(message, 'user');
        this.lastUserMessage = message; // 保存用户消息用于历史记录
        messageInput.value = '';
        messageInput.style.height = 'auto';
        
        // 立即显示"正在思考中"容器（通用设计）
        this.showThinkingContainer();
        
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

    /**
     * 处理表单输入请求
     */
    handleFormInputRequest(message) {
        console.log('处理表单输入请求:', message);
        
        // 停止加载状态
        this.setLoading(false);
        
        // 立即禁用输入框，防止用户在表单请求期间输入
        this.setInputDisabled(true, '请先点击"填写表单"按钮完成表单填写...');
        
        // 展示为聊天气泡，提供"填写表单"按钮，避免出现空消息
        const formPrompt = message.description || message.title || '请填写以下信息以继续规划您的旅行：';
        const schema = message.schema || {};
        
        // 构建一个包含按钮的聊天消息卡片
        const messagesContainer = document.getElementById('messagesContainer');
        const bubble = document.createElement('div');
        bubble.className = 'message agent-message';
        // 生成唯一ID避免重复
        const btnId = `openTravelFormBtn_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        bubble.innerHTML = `
            <div class="message-avatar"><i class="fas fa-robot"></i></div>
            <div class="message-content">
                <div class="message-header">
                    <span class="sender-name">旅游智能体</span>
                    <span class="message-time">${new Date().toLocaleTimeString('zh-CN', {hour: '2-digit', minute: '2-digit'})}</span>
                </div>
                <div class="message-text"></div>
                <div class="actions" style="margin-top: 12px;">
                    <button class="form-action-btn" data-form-btn="${btnId}">
                        <i class="fas fa-clipboard-list"></i>
                        <span>填写表单</span>
                    </button>
                </div>
            </div>
        `;
        // 填充富文本内容
        bubble.querySelector('.message-text').innerHTML = this.formatMessage(formPrompt);
        messagesContainer.appendChild(bubble);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
        
        // 绑定按钮事件：点击后再打开模态框（支持多次点击）
        const openBtn = bubble.querySelector(`[data-form-btn="${btnId}"]`);
        // 保存schema和formPrompt到按钮的data属性，确保可以重复打开
        openBtn.dataset.schema = JSON.stringify(schema);
        openBtn.dataset.title = message.title || '请完善行程关键信息';
        openBtn.dataset.description = formPrompt;
        
        openBtn.addEventListener('click', () => {
            const savedSchema = JSON.parse(openBtn.dataset.schema || '{}');
            // 注意：这里不需要再次禁用，因为已经在handleFormInputRequest中禁用了
            this.showTravelFormModal(savedSchema, openBtn.dataset.title, openBtn.dataset.description);
        });
    }

    /**
     * 启用/禁用聊天输入框
     * @param {boolean} disabled - true禁用，false启用
     * @param {string} placeholder - 禁用时显示的占位符文本（可选）
     */
    setInputDisabled(disabled, placeholder = null) {
        const messageInput = document.getElementById('messageInput');
        const sendBtn = document.getElementById('sendBtn');
        
        if (disabled) {
            messageInput.disabled = true;
            messageInput.setAttribute('data-form-disabled', 'true');
            messageInput.style.cursor = 'not-allowed';
            messageInput.style.opacity = '0.6';
            if (placeholder) {
                messageInput.placeholder = placeholder;
            }
            sendBtn.disabled = true;
            sendBtn.style.cursor = 'not-allowed';
            sendBtn.style.opacity = '0.6';
        } else {
            messageInput.removeAttribute('data-form-disabled');
            messageInput.disabled = false;
            messageInput.style.cursor = 'text';
            messageInput.style.opacity = '1';
            messageInput.placeholder = '请描述您的旅游需求，例如：我想去日本旅游7天，预算1万元...';
            // 只有在非loading状态时才启用发送按钮
            if (!this.isLoading) {
                sendBtn.disabled = false;
                sendBtn.style.cursor = 'pointer';
                sendBtn.style.opacity = '1';
            }
        }
    }

    /**
     * 显示旅游规划表单模态框
     */
    showTravelFormModal(schema, title, description) {
        // 注意：输入框已经在handleFormInputRequest时禁用了，这里只需要更新占位符
        this.setInputDisabled(true, '请先完成表单填写...');
        
        // 移除已存在的表单模态框
        const existingModal = document.getElementById('travelFormModal');
        if (existingModal) {
            existingModal.remove();
            // 如果之前有模态框被移除，保持禁用状态（因为表单请求还在）
        }

        // 创建表单模态框
        const modal = document.createElement('div');
        modal.id = 'travelFormModal';
        modal.className = 'modal-overlay';
        modal.innerHTML = `
            <div class="modal-content form-modal">
                <div class="modal-header">
                    <h3>${title}</h3>
                    <button class="close-btn" id="closeTravelFormBtn">×</button>
                </div>
                <div class="modal-body">
                    <p class="form-description">${description}</p>
                    <form id="travelForm">
                        <div class="form-group">
                            <label for="destination">目的地 <span class="required">*</span></label>
                            <input type="text" id="destination" name="destination" required placeholder="例如：云南 昆明-大理-丽江">
                        </div>
                        
                        <div class="form-row">
                            <div class="form-group">
                                <label for="startDate">出发日期</label>
                                <input type="date" id="startDate" name="startDate" placeholder="yyyy-MM-dd" inputmode="numeric" pattern="\\d{4}-\\d{2}-\\d{2}">
                            </div>
                            <div class="form-group">
                                <label for="days">旅行天数</label>
                                <input type="number" id="days" name="days" min="1" max="30" placeholder="天数">
                            </div>
                        </div>
                        
                        <div class="form-group">
                            <label for="peopleCount">人数 <span class="required">*</span></label>
                            <input type="number" id="peopleCount" name="peopleCount" min="1" max="20" required value="2">
                        </div>
                        
                        <div class="form-row">
                            <div class="form-group">
                                <label for="budgetRange">预算档位</label>
                                <select id="budgetRange" name="budgetRange">
                                    <option value="">请选择</option>
                                    <option value="economy">经济型（人均1000-2000元）</option>
                                    <option value="standard" selected>适中型（人均2000-5000元）</option>
                                    <option value="premium">高端型（人均5000元以上）</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label for="budgetAmount">预算金额（元）</label>
                                <input type="number" id="budgetAmount" name="budgetAmount" min="0" placeholder="可选">
                            </div>
                        </div>
                        
                        <div class="form-group">
                            <label for="preferences">偏好（可多选）</label>
                            <div class="checkbox-group">
                                <label><input type="checkbox" name="preferences" value="history">历史文化</label>
                                <label><input type="checkbox" name="preferences" value="food">美食</label>
                                <label><input type="checkbox" name="preferences" value="outdoor">户外运动</label>
                                <label><input type="checkbox" name="preferences" value="shopping">购物</label>
                                <label><input type="checkbox" name="preferences" value="family">亲子</label>
                                <label><input type="checkbox" name="preferences" value="relax">休闲</label>
                            </div>
                        </div>
                        
                        <div class="form-row">
                            <div class="form-group">
                                <label for="lodgingLevel">住宿标准</label>
                                <select id="lodgingLevel" name="lodgingLevel">
                                    <option value="">请选择</option>
                                    <option value="hostel">青旅</option>
                                    <option value="budget">经济型</option>
                                    <option value="comfort" selected>舒适型</option>
                                    <option value="luxury">高端型</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label for="transportPreference">交通偏好</label>
                                <select id="transportPreference" name="transportPreference">
                                    <option value="none" selected>无偏好</option>
                                    <option value="train">高铁</option>
                                    <option value="flight">飞机</option>
                                    <option value="self-drive">自驾</option>
                                </select>
                            </div>
                        </div>
                        
                        <div class="form-group">
                            <label for="notes">备注</label>
                            <textarea id="notes" name="notes" rows="3" maxlength="500" placeholder="其他需求或备注（可选）"></textarea>
                        </div>
                        
                        <div class="form-actions">
                            <button type="button" class="btn-secondary" id="cancelTravelFormBtn">取消</button>
                            <button type="submit" class="btn-primary">提交</button>
                        </div>
                    </form>
                </div>
            </div>
        `;
        
        document.body.appendChild(modal);
        
        // 关闭表单的回调函数（重新启用输入框）
        const closeFormAndEnableInput = () => {
            modal.remove();
            this.setInputDisabled(false);
        };
        
        // 绑定关闭按钮事件
        const closeBtn = document.getElementById('closeTravelFormBtn');
        closeBtn.addEventListener('click', closeFormAndEnableInput);
        
        // 绑定取消按钮事件
        const cancelBtn = document.getElementById('cancelTravelFormBtn');
        cancelBtn.addEventListener('click', closeFormAndEnableInput);
        
        // 点击遮罩层关闭模态框（可选，如果用户想要这个功能）
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeFormAndEnableInput();
            }
        });
        
        // 绑定表单提交事件
        const form = document.getElementById('travelForm');
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            this.submitTravelForm(form);
        });
        
        // 设置默认值（如果有）
        if (schema.defaults) {
            const defaults = schema.defaults;
            if (defaults.destination) document.getElementById('destination').value = defaults.destination;
            if (defaults.startDate) document.getElementById('startDate').value = defaults.startDate;
            if (defaults.days) document.getElementById('days').value = defaults.days;
            if (defaults.peopleCount) document.getElementById('peopleCount').value = defaults.peopleCount;
            if (defaults.budgetRange) document.getElementById('budgetRange').value = defaults.budgetRange;
            if (defaults.budgetAmount) document.getElementById('budgetAmount').value = defaults.budgetAmount;
            if (defaults.lodgingLevel) document.getElementById('lodgingLevel').value = defaults.lodgingLevel;
            if (defaults.transportPreference) document.getElementById('transportPreference').value = defaults.transportPreference;
        }
        
        // 显示模态框
        modal.style.display = 'flex';
    }

    /**
     * 提交旅游规划表单
     */
    submitTravelForm(form) {
        const formData = new FormData(form);
        
        // 构建表单数据对象
        const formPayload = {
            sessionId: this.currentSessionId,
            destination: formData.get('destination'),
            startDate: formData.get('startDate') || null,
            days: formData.get('days') ? parseInt(formData.get('days')) : null,
            peopleCount: parseInt(formData.get('peopleCount')),
            budgetRange: formData.get('budgetRange') || null,
            budgetAmount: formData.get('budgetAmount') ? parseFloat(formData.get('budgetAmount')) : null,
            preferences: formData.getAll('preferences'),
            lodgingLevel: formData.get('lodgingLevel') || null,
            transportPreference: formData.get('transportPreference') || 'none',
            notes: formData.get('notes') || null
        };
        
        // 验证必填字段
        if (!formPayload.destination || !formPayload.peopleCount) {
            alert('请填写必填字段：目的地和人数');
            return;
        }
        
        // 关闭模态框并重新启用输入框
        const modal = document.getElementById('travelFormModal');
        if (modal) {
            modal.remove();
        }
        this.setInputDisabled(false);
        
        // 显示提交提示
        this.setLoading(true);
        this.addMessage(`已提交表单：目的地=${formPayload.destination}，天数=${formPayload.days || '未指定'}，人数=${formPayload.peopleCount}人`, 'user');
        
        // 立即显示"正在思考中"容器（通用设计）
        this.showThinkingContainer();
        
        // 发送表单数据到后端
        try {
            this.sendWebSocketMessage('/app/traveling/form/submit', {
                form: formPayload
            });
        } catch (error) {
            console.error('提交表单失败:', error);
            this.addMessage('抱歉，提交表单时出现了错误。请稍后重试。', 'agent');
            this.setLoading(false);
        }
    }

    addMessage(content, sender) {
        if (!content || (typeof content === 'string' && content.trim().length === 0)) {
            console.log('⚠️ 跳过空消息渲染');
            return;
        }
        console.log(`💬 添加消息 - 发送者: ${sender}, 内容长度: ${content.length}`);
        console.log(`💬 消息内容预览: ${content.substring(0, 100)}...`);
        
        // 只对用户消息进行严格的去重检查
        // 智能体消息不进行去重，因为每条响应都应该显示
        if (sender === 'user') {
            const messagesContainer = document.getElementById('messagesContainer');
            const lastMessage = messagesContainer.lastElementChild;
            
            if (lastMessage && lastMessage.classList.contains('user-message')) {
                const lastMessageText = lastMessage.querySelector('.message-text');
                if (lastMessageText) {
                    const lastContent = lastMessageText.textContent || lastMessageText.innerText || '';
                    const currentContent = content.replace(/\s+/g, ' ').trim();
                    const lastContentNormalized = lastContent.replace(/\s+/g, ' ').trim();
                    
                    if (currentContent === lastContentNormalized) {
                        console.log('❌ 检测到重复的用户消息，跳过添加');
                        return;
                    }
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

    setLoading(loading, progressText = '智能体正在思考中...') {
        this.isLoading = loading;
        const sendBtn = document.getElementById('sendBtn');
        const messageInput = document.getElementById('messageInput');
        
        if (loading) {
            // 不显示单独的思考消息，等待工具执行容器
            // this.showThinkingMessage(progressText);
            
            // 禁用发送按钮
            sendBtn.disabled = true;
            sendBtn.style.cursor = 'not-allowed';
            sendBtn.style.opacity = '0.6';
            
            // 如果输入框没有被禁用（表单场景），则禁用输入框
            if (!messageInput.disabled) {
                messageInput.disabled = true;
                messageInput.style.cursor = 'not-allowed';
                messageInput.style.opacity = '0.6';
            }
        } else {
            // 移除思考中的消息
            this.hideThinkingMessage();
            
            // 只有在输入框没有被外部禁用（如表单场景）时才启用
            if (!messageInput.hasAttribute('data-form-disabled')) {
                sendBtn.disabled = false;
                sendBtn.style.cursor = 'pointer';
                sendBtn.style.opacity = '1';
                messageInput.disabled = false;
                messageInput.style.cursor = 'text';
                messageInput.style.opacity = '1';
            }
        }
    }
    
    /**
     * 显示思考中的消息
     */
    showThinkingMessage(text = '智能体正在思考中...') {
        // 先移除已存在的思考消息
        this.hideThinkingMessage();
        
        const messagesContainer = document.getElementById('messagesContainer');
        const thinkingBubble = document.createElement('div');
        thinkingBubble.className = 'message agent-message thinking-message';
        thinkingBubble.id = 'thinkingMessage';
        
        const currentTime = new Date().toLocaleTimeString('zh-CN', {hour: '2-digit', minute: '2-digit'});
        
        thinkingBubble.innerHTML = `
            <div class="message-avatar">
                <i class="fas fa-robot"></i>
            </div>
            <div class="message-content">
                <div class="message-header">
                    <span class="sender-name">旅游智能体</span>
                    <span class="message-time">${currentTime}</span>
                </div>
                <div class="message-text thinking-content">
                    <i class="fas fa-circle-notch fa-spin"></i>
                    <span>${text}</span>
                </div>
            </div>
        `;
        
        messagesContainer.appendChild(thinkingBubble);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
    
    /**
     * 更新思考消息的文本
     */
    updateThinkingMessage(text) {
        const thinkingMessage = document.getElementById('thinkingMessage');
        if (thinkingMessage) {
            const textSpan = thinkingMessage.querySelector('.thinking-content span');
            if (textSpan) {
                textSpan.textContent = text;
            }
        }
    }
    
    /**
     * 隐藏思考中的消息
     */
    hideThinkingMessage() {
        const thinkingMessage = document.getElementById('thinkingMessage');
        if (thinkingMessage) {
            thinkingMessage.remove();
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
        this.isLoading = false; // 重置加载状态
        
        // 清理已处理消息集合，避免跨会话消息干扰
        this.processedMessages.clear();
        
        // 清空消息容器并重新创建欢迎消息
        const messagesContainer = document.getElementById('messagesContainer');
        messagesContainer.innerHTML = '';
        
        // 清理可能残留的思考容器和工具执行容器（双重保险）
        const existingThinkingContainer = document.querySelector('.agent-thinking-container');
        if (existingThinkingContainer) {
            existingThinkingContainer.remove();
        }
        
        // 清理可能残留的思考消息
        const existingThinkingMessage = document.getElementById('thinkingMessage');
        if (existingThinkingMessage) {
            existingThinkingMessage.remove();
        }
        
        // 重新创建欢迎消息
        this.createWelcomeMessage();
        
        // 重置输入框
        const messageInput = document.getElementById('messageInput');
        messageInput.placeholder = '请描述您的旅游需求，例如：我想去日本旅游7天，预算1万元...';
        messageInput.disabled = false;
        messageInput.style.opacity = '1';
        messageInput.style.cursor = 'text';
        
        // 重置发送按钮
        const sendBtn = document.getElementById('sendBtn');
        sendBtn.disabled = false;
        sendBtn.style.cursor = 'pointer';
        sendBtn.style.opacity = '1';
        
        // 清空当前会话的临时存储
        this.clearCurrentSessionCache();
        
        // 更新聊天历史
        this.loadChatHistory();
        
        // 显示提示
        this.showNotification('新对话已开始', 'success');
        
        // 确保滚动到底部
        setTimeout(() => {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }, 100);
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
    /**
     * 处理工具执行信息
     * @param {Object} toolInfo 工具执行信息对象
     */
    handleToolExecution(toolInfo) {
        console.log('🔧 收到工具执行信息:', toolInfo);
        console.log('   批次ID:', toolInfo.batchId);
        console.log('   当前会话ID:', this.currentSessionId);
        
        const messagesContainer = document.getElementById('messagesContainer');
        if (!messagesContainer) {
            console.error('❌ 找不到消息容器');
            return;
        }
        
        // 检查是否有工具正在执行
        const hasExecuting = toolInfo.toolExecutions && toolInfo.toolExecutions.some(tool => tool.executing);
        console.log('工具执行状态 - hasExecuting:', hasExecuting);
        
        // 查找所有思考容器，使用最后一个（最新的）
        const allThinkingContainers = messagesContainer.querySelectorAll('.agent-thinking-container');
        let thinkingContainer = allThinkingContainers.length > 0 ? 
                                allThinkingContainers[allThinkingContainers.length - 1] : null;
        
        if (!thinkingContainer) {
            console.log('✨ 创建新的思考容器');
            thinkingContainer = this.createThinkingContainer();
            messagesContainer.appendChild(thinkingContainer);
        } else {
            console.log('📦 使用最新的思考容器 (共' + allThinkingContainers.length + '个)');
        }
        
        const toolList = thinkingContainer.querySelector('.tool-execution-list');
        if (!toolList) {
            console.error('❌ 找不到工具执行列表');
            return;
        }
        
        // 为每个工具创建独立的项
        toolInfo.toolExecutions.forEach((tool, index) => {
            const toolItemId = toolInfo.batchId ? `${toolInfo.batchId}_${index}` : `tool_${Date.now()}_${index}`;
            let existingToolItem = toolList.querySelector(`[data-tool-id="${toolItemId}"]`);
            
            if (hasExecuting && !existingToolItem) {
                // 执行前：创建新的工具执行项（显示转圈）
                console.log(`✨ 创建新的工具执行项: ${tool.toolName} (转圈状态)`);
                const toolItemHtml = this.createSingleToolItem(tool, toolItemId);
                toolList.insertAdjacentHTML('beforeend', toolItemHtml);
            } else if (!hasExecuting && existingToolItem) {
                // 执行后：更新已有的工具执行项（显示结果）
                console.log(`🔄 更新工具执行项: ${tool.toolName} (显示结果)`);
                const toolItemHtml = this.createSingleToolItem(tool, toolItemId);
                existingToolItem.outerHTML = toolItemHtml;
            } else if (!hasExecuting && !existingToolItem) {
                // 兼容处理：如果工具已完成但项不存在，创建新项
                console.log(`✨ 创建已完成工具执行项: ${tool.toolName} (显示结果)`);
                const toolItemHtml = this.createSingleToolItem(tool, toolItemId);
                toolList.insertAdjacentHTML('beforeend', toolItemHtml);
            }
        });
        
        // 滚动到底部（使用已声明的 messagesContainer）
        if (messagesContainer) {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
    }
    
    /**
     * 显示思考容器（通用方法，在发送消息后立即调用）
     */
    showThinkingContainer() {
        const messagesContainer = document.getElementById('messagesContainer');
        if (!messagesContainer) return;
        
        // 检查是否已存在思考容器
        let existingThinkingContainer = messagesContainer.querySelector('.agent-thinking-container');
        
        if (existingThinkingContainer) {
            console.log('📦 已存在思考容器，检查状态');
            
            // 检查思考容器是否已完成（显示"思考完成"）
            const thinkingLabel = existingThinkingContainer.querySelector('.thinking-label');
            if (thinkingLabel && thinkingLabel.textContent.includes('思考完成')) {
                console.log('✨ 之前的思考已完成，创建新的思考容器');
                // 创建新的思考容器，保留旧的
                const newThinkingContainer = this.createThinkingContainer();
                messagesContainer.appendChild(newThinkingContainer);
            } else {
                console.log('♻️ 重置现有思考容器状态');
                // 如果还在思考中或执行工具中，重置为"正在思考中"
                if (thinkingLabel) {
                    thinkingLabel.innerHTML = '<i class="fas fa-spinner fa-spin"></i><span>正在思考中...</span>';
                }
            }
        } else {
            console.log('✨ 创建新的思考容器');
            const thinkingContainer = this.createThinkingContainer();
            messagesContainer.appendChild(thinkingContainer);
        }
        
        // 确保滚动到底部
        setTimeout(() => {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }, 100);
    }
    
    /**
     * 隐藏思考容器（在收到最终响应后调用）
     */
    hideThinkingContainer() {
        const thinkingContainer = document.querySelector('.agent-thinking-container');
        if (thinkingContainer) {
            thinkingContainer.remove();
        }
    }
    
    /**
     * 创建"思考中"容器（统一容纳所有工具执行信息）
     */
    createThinkingContainer() {
        const container = document.createElement('div');
        container.className = 'message agent-message agent-thinking-container';
        
        const currentTime = new Date().toLocaleTimeString('zh-CN', {hour: '2-digit', minute: '2-digit'});
        
        container.innerHTML = `
            <div class="message-avatar">
                <i class="fas fa-robot"></i>
            </div>
            <div class="message-content">
                <div class="message-header">
                    <span class="sender-name">旅游智能体</span>
                    <span class="message-time">${currentTime}</span>
                </div>
                <div class="message-text thinking-content">
                    <div class="thinking-label">
                        <i class="fas fa-spinner fa-spin"></i>
                        <span>正在思考中...</span>
                    </div>
                    <div class="tool-execution-list">
                    </div>
                </div>
            </div>
        `;
        
        return container;
    }
    
    /**
     * 创建单个工具项的HTML
     */
    createSingleToolItem(tool, toolItemId) {
        // 根据执行状态显示不同的图标
        let statusIcon, statusClass;
        if (tool.executing) {
            statusIcon = '<i class="fas fa-circle-notch fa-spin"></i>';
            statusClass = 'executing';
        } else if (tool.success) {
            statusIcon = '<i class="fas fa-check-circle" style="color: #10b981;"></i>'; // 绿色勾选
            statusClass = 'success';
        } else {
            statusIcon = '<i class="fas fa-times-circle" style="color: #ef4444;"></i>';
            statusClass = 'failed';
        }
        
        // 格式化参数显示
        let argumentsDisplay = '无参数';
        if (tool.arguments) {
            try {
                const argsObj = JSON.parse(tool.arguments);
                argumentsDisplay = JSON.stringify(argsObj, null, 2);
                if (argumentsDisplay.length > 200) {
                    argumentsDisplay = argumentsDisplay.substring(0, 200) + '...';
                }
            } catch (e) {
                argumentsDisplay = tool.arguments.length > 200 
                    ? tool.arguments.substring(0, 200) + '...' 
                    : tool.arguments;
            }
        }
        
        return `
            <div class="tool-execution-item ${statusClass}" data-tool-id="${toolItemId}">
                <div class="tool-header" onclick="this.parentElement.classList.toggle('expanded')">
                    <div class="tool-main-info">
                        <span class="tool-status-icon">${statusIcon}</span>
                        <span class="tool-label">执行工具：</span>
                        <span class="tool-name">${this.escapeHtml(tool.toolName)}</span>
                    </div>
                    <i class="fas fa-chevron-down tool-expand-icon"></i>
                </div>
                <div class="tool-details">
                    <div class="tool-detail-section">
                        <div class="detail-label"><i class="fas fa-code"></i> 参数</div>
                        <div class="detail-content code-block">${this.escapeHtml(argumentsDisplay)}</div>
                    </div>
                    ${tool.executing ? `
                        <div class="tool-detail-section">
                            <div class="detail-label"><i class="fas fa-spinner fa-spin"></i> 状态</div>
                            <div class="detail-content">
                                <span class="executing-text">正在执行中...</span>
                            </div>
                        </div>
                    ` : tool.success ? `
                        <div class="tool-detail-section">
                            <div class="detail-label"><i class="fas fa-check"></i> 结果</div>
                            <div class="detail-content result-text">
                                ${this.formatResult(tool.result)}
                            </div>
                        </div>
                    ` : `
                        <div class="tool-detail-section">
                            <div class="detail-label"><i class="fas fa-exclamation-triangle"></i> 错误</div>
                            <div class="detail-content error-text">${this.escapeHtml(tool.errorMessage || tool.result || '未知错误')}</div>
                        </div>
                    `}
                </div>
            </div>
        `;
    }
    
    /**
     * 创建工具执行可视化组件
     * @param {Object} toolInfo 工具执行信息
     * @return {HTMLElement} 工具执行组件元素
     */
    createToolExecutionWidget(toolInfo) {
        const widget = document.createElement('div');
        widget.className = 'tool-execution-widget';
        
        const executionMode = toolInfo.executionMode === 'parallel' ? '并行执行' : '串行执行';
        const modeIcon = toolInfo.executionMode === 'parallel' ? '⚡' : '➡️';
        
        // 构建工具列表HTML
        const toolsListHtml = toolInfo.toolExecutions.map((tool, index) => {
            // 根据执行状态显示不同的图标
            let statusIcon, statusClass;
            if (tool.executing) {
                statusIcon = '<i class="fas fa-circle-notch fa-spin"></i>';
                statusClass = 'executing';
            } else if (tool.success) {
                statusIcon = '<i class="fas fa-check-circle" style="color: #10b981;"></i>'; // 绿色勾选
                statusClass = 'success';
            } else {
                statusIcon = '<i class="fas fa-times-circle"></i>';
                statusClass = 'failed';
            }
            
            // 格式化参数显示
            let argumentsDisplay = '无参数';
            if (tool.arguments) {
                try {
                    const argsObj = JSON.parse(tool.arguments);
                    argumentsDisplay = JSON.stringify(argsObj, null, 2);
                    if (argumentsDisplay.length > 200) {
                        argumentsDisplay = argumentsDisplay.substring(0, 200) + '...';
                    }
                } catch (e) {
                    argumentsDisplay = tool.arguments.length > 200 
                        ? tool.arguments.substring(0, 200) + '...' 
                        : tool.arguments;
                }
            }
            
            // 格式化结果显示（完整显示，不截断）
            let resultDisplay = tool.result || '无结果';
            
            return `
                <div class="tool-execution-item ${statusClass}" data-tool-index="${index}">
                    <div class="tool-header" onclick="this.parentElement.classList.toggle('expanded')">
                        <div class="tool-main-info">
                            <span class="tool-status-icon">${statusIcon}</span>
                            <span class="tool-label">执行工具：</span>
                            <span class="tool-name">${this.escapeHtml(tool.toolName)}</span>
                        </div>
                        <i class="fas fa-chevron-down tool-expand-icon"></i>
                    </div>
                    <div class="tool-details">
                        <div class="tool-detail-section">
                            <div class="detail-label"><i class="fas fa-code"></i> 参数</div>
                            <div class="detail-content code-block">${this.escapeHtml(argumentsDisplay)}</div>
                        </div>
                        ${tool.executing ? `
                            <div class="tool-detail-section">
                                <div class="detail-label"><i class="fas fa-spinner fa-spin"></i> 状态</div>
                                <div class="detail-content">
                                    <span class="executing-text">正在执行中...</span>
                                </div>
                            </div>
                        ` : tool.success ? `
                            <div class="tool-detail-section">
                                <div class="detail-label"><i class="fas fa-check"></i> 结果</div>
                                <div class="detail-content result-text">
                                    ${this.formatResult(tool.result)}
                                </div>
                            </div>
                        ` : `
                            <div class="tool-detail-section">
                                <div class="detail-label"><i class="fas fa-exclamation-triangle"></i> 错误</div>
                                <div class="detail-content error-text">${this.escapeHtml(tool.errorMessage || tool.result || '未知错误')}</div>
                            </div>
                        `}
                    </div>
                </div>
            `;
        }).join('');
        
        // 简化显示：只显示工具列表，删除标题行
        widget.innerHTML = `
            <div class="tool-execution-list">
                ${toolsListHtml}
            </div>
        `;
        
        return widget;
    }
    
    /**
     * 转义HTML字符
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    /**
     * 格式化工具执行结果
     */
    formatResult(result) {
        if (!result) return '<span class="empty-result">无结果</span>';
        
        // 如果是JSON格式，尝试美化显示
        if (result.trim().startsWith('{') || result.trim().startsWith('[')) {
            try {
                const parsed = JSON.parse(result);
                return `<pre class="json-result">${this.escapeHtml(JSON.stringify(parsed, null, 2))}</pre>`;
            } catch (e) {
                // 不是有效JSON，按普通文本处理
            }
        }
        
        // 普通文本，保留换行
        return `<div class="text-result">${this.escapeHtml(result).replace(/\n/g, '<br>')}</div>`;
    }

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
