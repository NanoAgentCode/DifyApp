/**
 * SVG下载功能脚本
 * 用于思维导图页面，提供下载SVG矢量图的功能
 */

// 等待页面加载完成
document.addEventListener('DOMContentLoaded', function() {
    // 延迟一点时间确保页面完全加载
    setTimeout(() => {
        addSaveButton();
    }, 2000);
});

function addSaveButton() {
    // 检查是否已经存在保存按钮
    if (document.getElementById('save-image-btn')) {
        return;
    }
    
    // 创建按钮容器
    const buttonContainer = document.createElement('div');
    buttonContainer.id = 'button-container';
    buttonContainer.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        z-index: 10000;
        display: flex;
        flex-direction: column;
        gap: 10px;
    `;
    
    const saveButton = document.createElement('button');
    saveButton.id = 'save-image-btn';
    saveButton.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
            <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
        </svg>
        <span style="margin-left: 8px;">下载SVG</span>
    `;
    saveButton.style.cssText = `
        padding: 10px 20px;
        background: #007bff;
        color: white;
        border: none;
        border-radius: 5px;
        cursor: pointer;
        font-size: 14px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.2);
        transition: all 0.3s ease;
        font-family: Arial, sans-serif;
        min-width: 140px;
        display: flex;
        align-items: center;
        justify-content: center;
    `;
    
    // 添加悬停效果
    saveButton.addEventListener('mouseenter', function() {
        this.style.background = '#0056b3';
        this.style.transform = 'translateY(-2px)';
    });
    
    saveButton.addEventListener('mouseleave', function() {
        this.style.background = '#007bff';
        this.style.transform = 'translateY(0)';
    });
    
    saveButton.onclick = saveAsVectorImage;
    
    // 创建取消按钮（初始隐藏）
    const cancelButton = document.createElement('button');
    cancelButton.id = 'cancel-btn';
    cancelButton.textContent = '取消生成';
    cancelButton.style.cssText = `
        padding: 8px 16px;
        background: #dc3545;
        color: white;
        border: none;
        border-radius: 5px;
        cursor: pointer;
        font-size: 12px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.2);
        transition: all 0.3s ease;
        font-family: Arial, sans-serif;
        display: none;
        min-width: 140px;
    `;
    
    cancelButton.onclick = cancelGeneration;
    
    // 添加到容器
    buttonContainer.appendChild(saveButton);
    buttonContainer.appendChild(cancelButton);
    document.body.appendChild(buttonContainer);
}

// 全局变量存储超时ID
let globalTimeoutId = null;

function cancelGeneration() {
    console.log('用户取消生成');
    
    // 清除所有超时
    if (globalTimeoutId) {
        clearTimeout(globalTimeoutId);
        globalTimeoutId = null;
    }
    
    // 隐藏取消按钮
    const cancelBtn = document.getElementById('cancel-btn');
    if (cancelBtn) {
        cancelBtn.style.display = 'none';
    }
    
    // 恢复保存按钮
    const saveBtn = document.getElementById('save-image-btn');
    if (saveBtn) {
        resetButton(saveBtn, '下载SVG');
    }
    
    showNotification('生成已取消', 'info');
    // 恢复页面操作
    enablePageOperations();
}

function saveAsVectorImage() {
    const button = document.getElementById('save-image-btn');
    const cancelBtn = document.getElementById('cancel-btn');
    const originalText = '下载SVG';
    
    // 禁止页面所有操作
    disablePageOperations();
    
    // 显示加载状态
    button.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle; animation: spin 1s linear infinite;">
            <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z"/>
            <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" fill="none"/>
        </svg>
        <span style="margin-left: 8px;">准备中...</span>
    `;
    button.disabled = true;
    button.style.background = '#6c757d';
    
    // 显示取消按钮
    if (cancelBtn) {
        cancelBtn.style.display = 'block';
    }
    
    // 等待SVG完全渲染
    setTimeout(() => {
        try {
            // 获取SVG元素
            const svgElement = document.querySelector('svg');
            if (!svgElement) {
                throw new Error('找不到SVG元素');
            }
            
            // 更新状态
            button.innerHTML = `
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
                    <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z"/>
                    <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" fill="none"/>
                </svg>
                <span style="margin-left: 8px;">生成SVG中...</span>
            `;
            
            // 克隆SVG元素以避免修改原始元素
            const clonedSvg = svgElement.cloneNode(true);
            
            // 设置SVG属性
            const bbox = svgElement.getBBox();
            const padding = 100; // 增加边距确保完整
            const width = Math.max(bbox.width + padding, 1000);
            const height = Math.max(bbox.height + padding, 800);
            
            clonedSvg.setAttribute('width', width);
            clonedSvg.setAttribute('height', height);
            clonedSvg.setAttribute('viewBox', `${bbox.x - padding/2} ${bbox.y - padding/2} ${width} ${height}`);
            
            // 添加白色背景
            const backgroundRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
            backgroundRect.setAttribute('width', width);
            backgroundRect.setAttribute('height', height);
            backgroundRect.setAttribute('fill', 'white');
            backgroundRect.setAttribute('x', bbox.x - padding/2);
            backgroundRect.setAttribute('y', bbox.y - padding/2);
            
            // 将背景插入到SVG开头
            clonedSvg.insertBefore(backgroundRect, clonedSvg.firstChild);
            
            // 转换为SVG字符串
            const svgString = new XMLSerializer().serializeToString(clonedSvg);
            
            // 创建SVG Blob
            const svgBlob = new Blob([svgString], {type: 'image/svg+xml'});
            const svgUrl = URL.createObjectURL(svgBlob);
            
            // 更新按钮状态
            button.innerHTML = `
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
                    <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
                </svg>
                <span style="margin-left: 8px;">下载中...</span>
            `;
            
            // 下载SVG文件
            const link = document.createElement('a');
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            link.download = `mindmap-vector-${timestamp}.svg`;
            link.href = svgUrl;
            link.click();
            
            // 清理
            URL.revokeObjectURL(svgUrl);
            
            // 清除超时
            clearTimeout(globalTimeoutId);
            globalTimeoutId = null;
            
            // 恢复按钮状态
            resetButton(button, originalText);
            
            // 恢复页面操作
            enablePageOperations();
            
            // 显示成功提示
            showNotification('SVG矢量图保存成功！支持任意缩放', 'success');
            
        } catch (error) {
            console.error('准备生成SVG时出错:', error);
            showNotification('准备生成SVG失败: ' + error.message, 'error');
            resetButton(button, originalText);
            // 隐藏取消按钮
            if (cancelBtn) {
                cancelBtn.style.display = 'none';
            }
            // 恢复页面操作
            enablePageOperations();
        }
    }, 1000);
}

function generateSVG(svgElement, button, originalText) {
    console.log('开始生成SVG矢量图');
    
    // 添加超时保护
    globalTimeoutId = setTimeout(() => {
        console.warn('生成超时');
        button.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
                <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z"/>
                <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" fill="none"/>
            </svg>
            <span style="margin-left: 8px;">超时，重试中...</span>
        `;
        tryAlternativeMethod(button, originalText);
    }, 15000); // 15秒超时
    
    try {
        // 获取SVG的完整尺寸
        const bbox = svgElement.getBBox();
        console.log('SVG边界信息:', bbox);
        
        // 计算完整的尺寸，确保包含所有内容
        const padding = 100; // 增加边距确保完整
        const width = Math.max(bbox.width + padding, 1000);
        const height = Math.max(bbox.height + padding, 800);
        
        console.log('SVG计算出的完整尺寸:', { 
            width: width, 
            height: height,
            padding: padding
        });
        
        // 更新按钮状态
        button.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
                <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z"/>
                <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" fill="none"/>
            </svg>
            <span style="margin-left: 8px;">生成SVG中...</span>
        `;
        
        // 克隆SVG元素以避免修改原始元素
        const clonedSvg = svgElement.cloneNode(true);
        
        // 设置SVG属性
        clonedSvg.setAttribute('width', width);
        clonedSvg.setAttribute('height', height);
        clonedSvg.setAttribute('viewBox', `${bbox.x - padding/2} ${bbox.y - padding/2} ${width} ${height}`);
        
        // 添加白色背景
        const backgroundRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        backgroundRect.setAttribute('width', width);
        backgroundRect.setAttribute('height', height);
        backgroundRect.setAttribute('fill', 'white');
        backgroundRect.setAttribute('x', bbox.x - padding/2);
        backgroundRect.setAttribute('y', bbox.y - padding/2);
        
        // 将背景插入到SVG开头
        clonedSvg.insertBefore(backgroundRect, clonedSvg.firstChild);
        
        // 转换为SVG字符串
        const svgString = new XMLSerializer().serializeToString(clonedSvg);
        
        // 创建SVG Blob
        const svgBlob = new Blob([svgString], {type: 'image/svg+xml'});
        const svgUrl = URL.createObjectURL(svgBlob);
        
        // 更新按钮状态
        button.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
                <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
            </svg>
            <span style="margin-left: 8px;">下载中...</span>
        `;
        
        // 下载SVG文件
        const link = document.createElement('a');
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        link.download = `mindmap-vector-${timestamp}.svg`;
        link.href = svgUrl;
        link.click();
        
        // 清理
        URL.revokeObjectURL(svgUrl);
        
        // 清除超时
        clearTimeout(globalTimeoutId);
        globalTimeoutId = null;
        
        // 恢复按钮状态
        resetButton(button, originalText);
        
        // 恢复页面操作
        enablePageOperations();
        
        // 显示成功提示
        showNotification('SVG矢量图保存成功！支持任意缩放', 'success');
        
    } catch (error) {
        clearTimeout(globalTimeoutId);
        globalTimeoutId = null;
        console.error('生成SVG失败:', error);
        showNotification('生成SVG失败: ' + error.message, 'error');
        resetButton(button, originalText);
        // 恢复页面操作
        enablePageOperations();
    }
}

function tryAlternativeMethod(button, originalText) {
    console.log('尝试备用方案: 使用较小边距');
    button.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
            <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z"/>
            <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" fill="none"/>
        </svg>
        <span style="margin-left: 8px;">备用方案中...</span>
    `;
    
    try {
        const svgElement = document.querySelector('svg');
        if (!svgElement) {
            throw new Error('找不到SVG元素');
        }
        
        // 使用较小的边距作为备用方案
        const bbox = svgElement.getBBox();
        const padding = 50;
        const width = Math.max(bbox.width + padding, 800);
        const height = Math.max(bbox.height + padding, 600);
        
        // 更新按钮状态
        button.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
                <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z"/>
                <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" fill="none"/>
            </svg>
            <span style="margin-left: 8px;">生成备用SVG...</span>
        `;
        
        // 克隆SVG元素
        const clonedSvg = svgElement.cloneNode(true);
        
        // 设置SVG属性
        clonedSvg.setAttribute('width', width);
        clonedSvg.setAttribute('height', height);
        clonedSvg.setAttribute('viewBox', `${bbox.x - padding/2} ${bbox.y - padding/2} ${width} ${height}`);
        
        // 添加白色背景
        const backgroundRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        backgroundRect.setAttribute('width', width);
        backgroundRect.setAttribute('height', height);
        backgroundRect.setAttribute('fill', 'white');
        backgroundRect.setAttribute('x', bbox.x - padding/2);
        backgroundRect.setAttribute('y', bbox.y - padding/2);
        
        // 将背景插入到SVG开头
        clonedSvg.insertBefore(backgroundRect, clonedSvg.firstChild);
        
        // 转换为SVG字符串
        const svgString = new XMLSerializer().serializeToString(clonedSvg);
        
        // 创建SVG Blob
        const svgBlob = new Blob([svgString], {type: 'image/svg+xml'});
        const svgUrl = URL.createObjectURL(svgBlob);
        
        // 更新按钮状态
        button.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
                <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
            </svg>
            <span style="margin-left: 8px;">下载备用SVG...</span>
        `;
        
        // 下载SVG文件
        const link = document.createElement('a');
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        link.download = `mindmap-backup-${timestamp}.svg`;
        link.href = svgUrl;
        link.click();
        
        // 清理
        URL.revokeObjectURL(svgUrl);
        
        // 恢复按钮状态
        resetButton(button, originalText);
        
        // 恢复页面操作
        enablePageOperations();
        
        // 显示成功提示
        showNotification('备用SVG矢量图保存成功！', 'success');
        
    } catch (error) {
        console.error('备用方案也失败了:', error);
        showNotification('所有方案都失败了，请重试', 'error');
        resetButton(button, originalText);
        // 恢复页面操作
        enablePageOperations();
    }
}

function resetButton(button, originalText) {
    // 恢复按钮状态
    button.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style="display: inline-block; vertical-align: middle;">
            <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
        </svg>
        <span style="margin-left: 8px;">${originalText}</span>
    `;
    button.disabled = false;
    button.style.background = '#007bff';
    
    // 隐藏取消按钮
    const cancelBtn = document.getElementById('cancel-btn');
    if (cancelBtn) {
        cancelBtn.style.display = 'none';
    }
    
    // 清除全局超时ID
    if (globalTimeoutId) {
        clearTimeout(globalTimeoutId);
        globalTimeoutId = null;
    }
}

function showNotification(message, type) {
    // 创建通知元素
    const notification = document.createElement('div');
    
    // 根据类型设置样式
    let backgroundColor = '#007bff'; // 默认蓝色
    if (type === 'success') {
        backgroundColor = '#28a745'; // 成功绿色
    } else if (type === 'error') {
        backgroundColor = '#dc3545'; // 错误红色
    } else if (type === 'info') {
        backgroundColor = '#17a2b8'; // 信息蓝色
    }
    
    notification.style.cssText = `
        position: fixed;
        top: 80px;
        right: 20px;
        padding: 15px 20px;
        border-radius: 5px;
        color: white;
        font-size: 14px;
        z-index: 10001;
        animation: slideIn 0.3s ease;
        font-family: Arial, sans-serif;
        background: ${backgroundColor};
    `;
    notification.textContent = message;
    
    // 添加动画样式
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
    `;
    document.head.appendChild(style);
    
    document.body.appendChild(notification);
    
    // 3秒后自动移除
    setTimeout(() => {
        notification.remove();
    }, 3000);
}

function disablePageOperations() {
    console.log('禁止页面所有操作');
    
    // 创建遮罩层
    const overlay = document.createElement('div');
    overlay.id = 'operation-overlay';
    overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: rgba(0, 0, 0, 0.3);
        z-index: 9999;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-size: 18px;
        font-family: Arial, sans-serif;
        backdrop-filter: blur(2px);
    `;
    overlay.innerHTML = `
        <div style="text-align: center; background: rgba(0, 0, 0, 0.8); padding: 30px; border-radius: 10px; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);">
            <div style="margin-bottom: 20px;">
                <svg width="50" height="50" viewBox="0 0 50 50" style="animation: spin 1s linear infinite;">
                    <circle cx="25" cy="25" r="20" stroke="#007bff" stroke-width="4" fill="none" stroke-dasharray="31.416" stroke-dashoffset="31.416">
                        <animate attributeName="stroke-dashoffset" values="31.416;0" dur="1s" repeatCount="indefinite"/>
                    </svg>
                </svg>
            </div>
            <div>正在生成SVG矢量图...</div>
            <div style="font-size: 14px; margin-top: 10px; opacity: 0.8;">请勿进行任何操作，以免影响生成过程</div>
        </div>
    `;
    
    // 添加旋转动画样式
    const style = document.createElement('style');
    style.textContent = `
        @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
        }
    `;
    document.head.appendChild(style);
    
    document.body.appendChild(overlay);
    
    // 禁止滚动
    document.body.style.overflow = 'hidden';
    
    // 禁止选择文本
    document.body.style.userSelect = 'none';
    document.body.style.webkitUserSelect = 'none';
    document.body.style.mozUserSelect = 'none';
    document.body.style.msUserSelect = 'none';
    
    // 禁止右键菜单
    document.addEventListener('contextmenu', preventDefault, true);
    
    // 禁止键盘操作
    document.addEventListener('keydown', preventDefault, true);
    
    // 禁止鼠标拖拽
    document.addEventListener('dragstart', preventDefault, true);
    document.addEventListener('drop', preventDefault, true);
    
    // 禁止触摸操作
    document.addEventListener('touchstart', preventDefault, true);
    document.addEventListener('touchmove', preventDefault, true);
    document.addEventListener('touchend', preventDefault, true);
    
    // 禁止滚轮事件
    document.addEventListener('wheel', preventDefault, true);
    
    // 禁止所有点击事件（除了取消按钮）
    document.addEventListener('click', preventClick, true);
    document.addEventListener('mousedown', preventDefault, true);
    document.addEventListener('mouseup', preventDefault, true);
}

function enablePageOperations() {
    console.log('恢复页面所有操作');
    
    // 移除遮罩层
    const overlay = document.getElementById('operation-overlay');
    if (overlay) {
        overlay.remove();
    }
    
    // 恢复滚动
    document.body.style.overflow = '';
    
    // 恢复文本选择
    document.body.style.userSelect = '';
    document.body.style.webkitUserSelect = '';
    document.body.style.mozUserSelect = '';
    document.body.style.msUserSelect = '';
    
    // 移除所有事件监听器
    document.removeEventListener('contextmenu', preventDefault, true);
    document.removeEventListener('keydown', preventDefault, true);
    document.removeEventListener('dragstart', preventDefault, true);
    document.removeEventListener('drop', preventDefault, true);
    document.removeEventListener('touchstart', preventDefault, true);
    document.removeEventListener('touchmove', preventDefault, true);
    document.removeEventListener('touchend', preventDefault, true);
    document.removeEventListener('wheel', preventDefault, true);
    document.removeEventListener('click', preventClick, true);
    document.removeEventListener('mousedown', preventDefault, true);
    document.removeEventListener('mouseup', preventDefault, true);
}

function preventDefault(event) {
    event.preventDefault();
    event.stopPropagation();
    return false;
}

function preventClick(event) {
    // 允许取消按钮的点击
    if (event.target.id === 'cancel-btn' || event.target.closest('#cancel-btn')) {
        return true;
    }
    
    // 允许保存按钮的点击（虽然它应该是禁用的）
    if (event.target.id === 'save-image-btn' || event.target.closest('#save-image-btn')) {
        return true;
    }
    
    // 阻止其他所有点击
    event.preventDefault();
    event.stopPropagation();
    return false;
}

