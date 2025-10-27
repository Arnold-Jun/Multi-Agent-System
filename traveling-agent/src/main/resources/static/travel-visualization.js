/**
 * 旅游可视化组件
 * 提供旅游相关的动画效果和可视化元素
 */

class TravelVisualization {
    constructor() {
        this.animations = new Map();
        this.init();
    }

    init() {
        this.createFloatingElements();
        this.addTravelIcons();
        this.setupParallaxEffects();
    }

    createFloatingElements() {
        const container = document.querySelector('.messages-container');
        if (!container) return;

        // 创建浮动元素
        const floatingElements = [
            { icon: '✈️', delay: 0, duration: 8000 },
            { icon: '🏖️', delay: 2000, duration: 10000 },
            { icon: '🗺️', delay: 4000, duration: 12000 },
            { icon: '🏨', delay: 6000, duration: 9000 },
            { icon: '🍽️', delay: 8000, duration: 11000 },
            { icon: '📸', delay: 10000, duration: 13000 }
        ];

        floatingElements.forEach((element, index) => {
            setTimeout(() => {
                this.createFloatingElement(element.icon, element.duration);
            }, element.delay);
        });
    }

    createFloatingElement(icon, duration) {
        const container = document.querySelector('.messages-container');
        if (!container) return;

        const element = document.createElement('div');
        element.className = 'floating-element';
        element.textContent = icon;
        element.style.cssText = `
            position: absolute;
            font-size: 24px;
            opacity: 0.1;
            pointer-events: none;
            z-index: 1;
            animation: float ${duration}ms linear infinite;
            left: ${Math.random() * 100}%;
            top: ${Math.random() * 100}%;
        `;

        container.appendChild(element);

        // 动画结束后移除元素
        setTimeout(() => {
            if (element.parentNode) {
                element.parentNode.removeChild(element);
            }
        }, duration);
    }

    addTravelIcons() {
        // 为消息添加旅游相关的图标
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                mutation.addedNodes.forEach((node) => {
                    if (node.nodeType === Node.ELEMENT_NODE && node.classList.contains('agent-message')) {
                        this.enhanceMessageWithIcons(node);
                    }
                });
            });
        });

        const messagesContainer = document.querySelector('.messages-container');
        if (messagesContainer) {
            observer.observe(messagesContainer, { childList: true });
        }
    }

    enhanceMessageWithIcons(messageElement) {
        const messageText = messageElement.querySelector('.message-text');
        if (!messageText) return;

        // 使用innerHTML而不是textContent，以保留HTML标签
        const htmlContent = messageText.innerHTML;
        
        // 根据关键词添加图标
        const iconMap = {
            '机票': '✈️',
            '酒店': '🏨',
            '景点': '🏛️',
            '美食': '🍽️',
            '交通': '🚗',
            '天气': '🌤️',
            '预算': '💰',
            '行程': '🗓️',
            '预订': '📅',
            '旅游': '🧳',
            '目的地': '📍',
            '推荐': '⭐',
            '价格': '💵',
            '时间': '⏰',
            '地点': '📍'
        };

        let enhancedHtml = htmlContent;
        Object.entries(iconMap).forEach(([keyword, icon]) => {
            // 使用更精确的正则表达式，避免在HTML标签内替换
            const regex = new RegExp(`(^|>|\\s)(${keyword})(?=\\s|<|$)`, 'g');
            enhancedHtml = enhancedHtml.replace(regex, `$1${icon} $2`);
        });

        // 只有当内容确实发生变化时才更新innerHTML
        if (enhancedHtml !== htmlContent) {
            messageText.innerHTML = enhancedHtml;
        }
    }

    setupParallaxEffects() {
        // 为侧边栏添加视差效果
        const sidebar = document.querySelector('.sidebar');
        if (!sidebar) return;

        window.addEventListener('scroll', () => {
            const scrolled = window.pageYOffset;
            const rate = scrolled * -0.5;
            sidebar.style.transform = `translateY(${rate}px)`;
        });
    }

    showTravelProgress(step, total) {
        const progressBar = this.createProgressBar(step, total);
        const messagesContainer = document.querySelector('.messages-container');
        
        if (messagesContainer) {
            messagesContainer.appendChild(progressBar);
            
            // 3秒后移除进度条
            setTimeout(() => {
                if (progressBar.parentNode) {
                    progressBar.parentNode.removeChild(progressBar);
                }
            }, 3000);
        }
    }

    createProgressBar(step, total) {
        const progressContainer = document.createElement('div');
        progressContainer.className = 'travel-progress';
        progressContainer.style.cssText = `
            position: sticky;
            top: 20px;
            background: var(--bg-primary);
            border: 1px solid var(--border-color);
            border-radius: var(--border-radius);
            padding: var(--spacing-md);
            margin: var(--spacing-md) 0;
            box-shadow: var(--shadow-md);
            z-index: 10;
        `;

        const progressSteps = [
            '🔍 搜索信息',
            '📋 制定计划',
            '🏨 预订服务',
            '✅ 完成规划'
        ];

        const currentStep = Math.min(step, progressSteps.length - 1);
        const progress = ((step + 1) / total) * 100;

        progressContainer.innerHTML = `
            <div class="progress-header">
                <h4>旅游规划进度</h4>
                <span class="progress-text">${step + 1}/${total}</span>
            </div>
            <div class="progress-bar">
                <div class="progress-fill" style="width: ${progress}%"></div>
            </div>
            <div class="progress-steps">
                ${progressSteps.map((stepText, index) => `
                    <div class="progress-step ${index <= currentStep ? 'active' : ''}">
                        <span class="step-icon">${stepText.split(' ')[0]}</span>
                        <span class="step-text">${stepText.split(' ').slice(1).join(' ')}</span>
                    </div>
                `).join('')}
            </div>
        `;

        return progressContainer;
    }

    showTravelMap(destinations) {
        const mapContainer = document.createElement('div');
        mapContainer.className = 'travel-map';
        mapContainer.style.cssText = `
            background: var(--bg-primary);
            border: 1px solid var(--border-color);
            border-radius: var(--border-radius);
            padding: var(--spacing-lg);
            margin: var(--spacing-md) 0;
            box-shadow: var(--shadow-md);
        `;

        mapContainer.innerHTML = `
            <h4>🗺️ 旅游路线图</h4>
            <div class="map-route">
                ${destinations.map((dest, index) => `
                    <div class="map-destination">
                        <div class="destination-marker">${index + 1}</div>
                        <div class="destination-info">
                            <strong>${dest.name}</strong>
                            <span class="destination-duration">${dest.duration}</span>
                        </div>
                    </div>
                    ${index < destinations.length - 1 ? '<div class="route-connector">→</div>' : ''}
                `).join('')}
            </div>
        `;

        return mapContainer;
    }

    showWeatherWidget(location, weather) {
        const weatherWidget = document.createElement('div');
        weatherWidget.className = 'weather-widget';
        weatherWidget.style.cssText = `
            background: linear-gradient(135deg, #74b9ff, #0984e3);
            color: white;
            border-radius: var(--border-radius);
            padding: var(--spacing-lg);
            margin: var(--spacing-md) 0;
            box-shadow: var(--shadow-md);
        `;

        const weatherIcons = {
            'sunny': '☀️',
            'cloudy': '☁️',
            'rainy': '🌧️',
            'snowy': '❄️',
            'stormy': '⛈️'
        };

        weatherWidget.innerHTML = `
            <div class="weather-header">
                <h4>🌤️ ${location} 天气</h4>
                <span class="weather-icon">${weatherIcons[weather.condition] || '🌤️'}</span>
            </div>
            <div class="weather-details">
                <div class="weather-temp">${weather.temperature}°C</div>
                <div class="weather-condition">${weather.condition}</div>
                <div class="weather-extra">
                    <span>湿度: ${weather.humidity}%</span>
                    <span>风速: ${weather.windSpeed} km/h</span>
                </div>
            </div>
        `;

        return weatherWidget;
    }

    showBudgetBreakdown(budget) {
        const budgetWidget = document.createElement('div');
        budgetWidget.className = 'budget-widget';
        budgetWidget.style.cssText = `
            background: var(--bg-primary);
            border: 1px solid var(--border-color);
            border-radius: var(--border-radius);
            padding: var(--spacing-lg);
            margin: var(--spacing-md) 0;
            box-shadow: var(--shadow-md);
        `;

        const total = budget.reduce((sum, item) => sum + item.amount, 0);

        budgetWidget.innerHTML = `
            <h4>💰 预算明细</h4>
            <div class="budget-items">
                ${budget.map(item => `
                    <div class="budget-item">
                        <div class="budget-category">
                            <span class="budget-icon">${item.icon}</span>
                            <span class="budget-name">${item.category}</span>
                        </div>
                        <div class="budget-amount">¥${item.amount.toLocaleString()}</div>
                        <div class="budget-percentage">${((item.amount / total) * 100).toFixed(1)}%</div>
                    </div>
                `).join('')}
            </div>
            <div class="budget-total">
                <strong>总预算: ¥${total.toLocaleString()}</strong>
            </div>
        `;

        return budgetWidget;
    }

    animateMessage(messageElement) {
        // 为消息添加打字机效果
        const messageText = messageElement.querySelector('.message-text');
        if (!messageText) return;

        // 获取原始HTML内容
        const originalHtml = messageText.innerHTML;
        
        // 如果内容为空或太短，跳过动画
        if (!originalHtml || originalHtml.trim().length < 10) {
            return;
        }

        // 清空内容
        messageText.innerHTML = '';
        
        // 创建一个临时元素来解析HTML
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = originalHtml;
        
        // 获取纯文本内容用于打字机效果
        const textContent = tempDiv.textContent || tempDiv.innerText || '';
        
        // 如果纯文本为空，直接显示原始HTML
        if (!textContent.trim()) {
            messageText.innerHTML = originalHtml;
            return;
        }
        
        let index = 0;
        const typeWriter = () => {
            if (index < textContent.length) {
                // 逐步添加字符
                const currentText = textContent.substring(0, index + 1);
                
                // 重新构建HTML，保持原有的HTML结构
                let newHtml = originalHtml;
                
                // 找到当前文本在原始HTML中的位置，并截取到该位置
                let htmlIndex = 0;
                let textIndex = 0;
                
                while (htmlIndex < originalHtml.length && textIndex < currentText.length) {
                    const char = originalHtml[htmlIndex];
                    if (char === '<') {
                        // 跳过HTML标签
                        while (htmlIndex < originalHtml.length && originalHtml[htmlIndex] !== '>') {
                            htmlIndex++;
                        }
                        if (htmlIndex < originalHtml.length) {
                            htmlIndex++; // 跳过 '>'
                        }
                    } else if (char === '&') {
                        // 跳过HTML实体
                        const entityEnd = originalHtml.indexOf(';', htmlIndex);
                        if (entityEnd !== -1) {
                            htmlIndex = entityEnd + 1;
                        } else {
                            htmlIndex++;
                        }
                        textIndex++;
                    } else {
                        htmlIndex++;
                        textIndex++;
                    }
                }
                
                // 截取到当前位置的HTML
                newHtml = originalHtml.substring(0, htmlIndex);
                
                messageText.innerHTML = newHtml;
                index++;
                setTimeout(typeWriter, 30);
            } else {
                // 动画完成，确保显示完整的原始HTML
                messageText.innerHTML = originalHtml;
            }
        };

        typeWriter();
    }

    showCelebration() {
        // 显示庆祝动画
        const celebration = document.createElement('div');
        celebration.className = 'celebration';
        celebration.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            pointer-events: none;
            z-index: 1000;
        `;

        const emojis = ['🎉', '✨', '🌟', '🎊', '🎈'];
        
        for (let i = 0; i < 20; i++) {
            const emoji = document.createElement('div');
            emoji.textContent = emojis[Math.floor(Math.random() * emojis.length)];
            emoji.style.cssText = `
                position: absolute;
                font-size: 24px;
                left: ${Math.random() * 100}%;
                top: ${Math.random() * 100}%;
                animation: celebration 3s ease-out forwards;
            `;
            celebration.appendChild(emoji);
        }

        document.body.appendChild(celebration);

        setTimeout(() => {
            if (celebration.parentNode) {
                celebration.parentNode.removeChild(celebration);
            }
        }, 3000);
    }
}

// 添加CSS动画
const travelStyles = document.createElement('style');
travelStyles.textContent = `
    @keyframes float {
        0% {
            transform: translateY(0px) rotate(0deg);
            opacity: 0.1;
        }
        50% {
            transform: translateY(-20px) rotate(180deg);
            opacity: 0.3;
        }
        100% {
            transform: translateY(0px) rotate(360deg);
            opacity: 0.1;
        }
    }

    @keyframes celebration {
        0% {
            transform: translateY(0) scale(0);
            opacity: 1;
        }
        50% {
            transform: translateY(-100px) scale(1);
            opacity: 1;
        }
        100% {
            transform: translateY(-200px) scale(0);
            opacity: 0;
        }
    }

    .travel-progress {
        animation: slideInDown 0.5s ease-out;
    }

    .progress-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: var(--spacing-sm);
    }

    .progress-header h4 {
        margin: 0;
        color: var(--text-primary);
    }

    .progress-text {
        font-size: var(--font-size-sm);
        color: var(--text-secondary);
        font-weight: 600;
    }

    .progress-bar {
        height: 8px;
        background: var(--bg-tertiary);
        border-radius: 4px;
        overflow: hidden;
        margin-bottom: var(--spacing-md);
    }

    .progress-fill {
        height: 100%;
        background: linear-gradient(90deg, var(--primary-color), var(--success-color));
        border-radius: 4px;
        transition: width 0.3s ease;
    }

    .progress-steps {
        display: flex;
        justify-content: space-between;
        gap: var(--spacing-sm);
    }

    .progress-step {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--spacing-xs);
        flex: 1;
        opacity: 0.5;
        transition: opacity 0.3s ease;
    }

    .progress-step.active {
        opacity: 1;
    }

    .step-icon {
        font-size: var(--font-size-lg);
    }

    .step-text {
        font-size: var(--font-size-xs);
        text-align: center;
        color: var(--text-secondary);
    }

    .map-route {
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);
        flex-wrap: wrap;
    }

    .map-destination {
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);
        background: var(--bg-tertiary);
        padding: var(--spacing-sm);
        border-radius: var(--border-radius-sm);
    }

    .destination-marker {
        width: 24px;
        height: 24px;
        background: var(--primary-color);
        color: white;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: var(--font-size-xs);
        font-weight: 600;
    }

    .destination-info {
        display: flex;
        flex-direction: column;
        gap: 2px;
    }

    .destination-duration {
        font-size: var(--font-size-xs);
        color: var(--text-muted);
    }

    .route-connector {
        color: var(--text-muted);
        font-weight: bold;
    }

    .weather-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: var(--spacing-md);
    }

    .weather-header h4 {
        margin: 0;
    }

    .weather-icon {
        font-size: 2rem;
    }

    .weather-details {
        text-align: center;
    }

    .weather-temp {
        font-size: 2.5rem;
        font-weight: 700;
        margin-bottom: var(--spacing-sm);
    }

    .weather-condition {
        font-size: var(--font-size-lg);
        margin-bottom: var(--spacing-sm);
        text-transform: capitalize;
    }

    .weather-extra {
        display: flex;
        justify-content: space-around;
        font-size: var(--font-size-sm);
        opacity: 0.9;
    }

    .budget-items {
        margin-bottom: var(--spacing-md);
    }

    .budget-item {
        display: flex;
        align-items: center;
        gap: var(--spacing-md);
        padding: var(--spacing-sm) 0;
        border-bottom: 1px solid var(--border-color);
    }

    .budget-item:last-child {
        border-bottom: none;
    }

    .budget-category {
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);
        flex: 1;
    }

    .budget-icon {
        font-size: var(--font-size-lg);
    }

    .budget-name {
        font-weight: 500;
    }

    .budget-amount {
        font-weight: 600;
        color: var(--text-primary);
    }

    .budget-percentage {
        font-size: var(--font-size-sm);
        color: var(--text-muted);
        min-width: 40px;
        text-align: right;
    }

    .budget-total {
        text-align: center;
        padding-top: var(--spacing-md);
        border-top: 2px solid var(--primary-color);
        font-size: var(--font-size-lg);
        color: var(--primary-color);
    }

    @keyframes slideInDown {
        from {
            opacity: 0;
            transform: translateY(-20px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
`;

document.head.appendChild(travelStyles);

// 导出类供全局使用
window.TravelVisualization = TravelVisualization;
