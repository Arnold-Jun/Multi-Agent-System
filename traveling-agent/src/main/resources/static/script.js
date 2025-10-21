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
        
        this.init();
    }

    init() {
        this.bindEvents();
        this.loadSettings();
        this.checkAgentStatus();
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

    async sendMessage() {
        const messageInput = document.getElementById('messageInput');
        const message = messageInput.value.trim();
        
        if (!message || this.isLoading) return;

        // 添加用户消息到界面
        this.addMessage(message, 'user');
        messageInput.value = '';
        messageInput.style.height = 'auto';

        // 显示加载状态
        this.setLoading(true);

        try {
            // 调用后端API
            const response = await this.callTravelingAgent(message);
            
            // 添加智能体回复
            this.addMessage(response, 'agent');
            
            // 解析特殊内容（地图、天气、预算等）
            this.parseSpecialContent(response);
            
            // 保存到历史记录
            this.saveToHistory(message, response);
            
        } catch (error) {
            console.error('发送消息失败:', error);
            this.addMessage('抱歉，处理您的请求时出现了错误。请稍后重试。', 'agent');
        } finally {
            this.setLoading(false);
        }
    }

    async callTravelingAgent(message) {
        const response = await fetch('/api/traveling/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                sessionId: this.currentSessionId,
                chat: message
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        return await response.text();
    }

    addMessage(content, sender) {
        const messagesContainer = document.getElementById('messagesContainer');
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
            // 先处理Markdown格式
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
        this.currentSessionId = this.generateSessionId();
        this.messageHistory = [];
        
        // 清空消息容器，保留欢迎消息
        const messagesContainer = document.getElementById('messagesContainer');
        const welcomeMessage = messagesContainer.querySelector('.agent-message');
        messagesContainer.innerHTML = '';
        if (welcomeMessage) {
            messagesContainer.appendChild(welcomeMessage);
        }
        
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
            agent: agentResponse
        });
        
        // 保存到localStorage
        localStorage.setItem('travelingAgentHistory', JSON.stringify(this.messageHistory));
    }

    loadChatHistory() {
        const historyContainer = document.getElementById('chatHistory');
        const saved = localStorage.getItem('travelingAgentHistory');
        
        if (saved) {
            this.messageHistory = JSON.parse(saved);
        }
        
        // 显示最近的对话
        historyContainer.innerHTML = '';
        const recentChats = this.messageHistory.slice(-10).reverse();
        
        recentChats.forEach((chat, index) => {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';
            historyItem.innerHTML = `
                <div class="history-preview">
                    <div class="history-user-message">${this.truncateText(chat.user, 30)}</div>
                    <div class="history-time">${new Date(chat.timestamp).toLocaleString('zh-CN')}</div>
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
