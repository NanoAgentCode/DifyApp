<template>
  <div class="features-view">
    <div class="feature-entries">
      <div class="feature-header">
        <p class="feature-subtitle">选择功能模块，快速开始您的工作</p>
      </div>
      
      <div class="feature-categories">
        <!-- AI 应用类 -->
        <div class="feature-category">
          <h3 class="category-title">
            <el-icon class="category-icon"><Grid /></el-icon>
            <span>AI 应用</span>
          </h3>
          <div class="feature-cards">
            <div 
              class="feature-card" 
              :class="{ 'navigating': isNavigating }"
              @click="$emit('feature-click', 'apps')"
              role="button"
              tabindex="0"
              @keydown.enter="$emit('feature-click', 'apps')"
            >
              <div class="feature-card-icon">
                <el-icon class="feature-icon"><Grid /></el-icon>
              </div>
              <div class="feature-card-content">
                <h4 class="feature-card-title">智能应用</h4>
                <p class="feature-card-desc">创建和管理AI应用，构建智能工作流</p>
              </div>
              <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
            </div>
            
            <div 
              class="feature-card" 
              :class="{ 'navigating': isNavigating }"
              @click="$emit('feature-click', 'ai-drawio')"
              role="button"
              tabindex="0"
              @keydown.enter="$emit('feature-click', 'ai-drawio')"
            >
              <div class="feature-card-icon">
                <el-icon class="feature-icon"><Picture /></el-icon>
              </div>
              <div class="feature-card-content">
                <h4 class="feature-card-title">智能框图</h4>
                <p class="feature-card-desc">AI驱动的思维导图和流程图生成</p>
              </div>
              <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>

        <!-- 知识管理类 -->
        <div class="feature-category">
          <h3 class="category-title">
            <el-icon class="category-icon"><Folder /></el-icon>
            <span>知识管理</span>
          </h3>
          <div class="feature-cards">
            <div 
              class="feature-card" 
              :class="{ 'navigating': isNavigating }"
              @click="$emit('feature-click', 'kb-qa')"
              role="button"
              tabindex="0"
              @keydown.enter="$emit('feature-click', 'kb-qa')"
            >
              <div class="feature-card-icon">
                <el-icon class="feature-icon"><Search /></el-icon>
              </div>
              <div class="feature-card-content">
                <h4 class="feature-card-title">知识检索</h4>
                <p class="feature-card-desc">基于知识库的智能问答和检索</p>
              </div>
              <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
            </div>
            
            <div 
              class="feature-card" 
              :class="{ 'navigating': isNavigating }"
              @click="$emit('feature-click', 'knowledge-base')"
              role="button"
              tabindex="0"
              @keydown.enter="$emit('feature-click', 'knowledge-base')"
            >
              <div class="feature-card-icon">
                <el-icon class="feature-icon"><Folder /></el-icon>
              </div>
              <div class="feature-card-content">
                <h4 class="feature-card-title">知识管理</h4>
                <p class="feature-card-desc">管理知识库和文档，构建知识体系</p>
              </div>
              <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
            </div>
            
            <div 
              class="feature-card" 
              :class="{ 'navigating': isNavigating }"
              @click="$emit('feature-click', 'document')"
              role="button"
              tabindex="0"
              @keydown.enter="$emit('feature-click', 'document')"
            >
              <div class="feature-card-icon">
                <el-icon class="feature-icon"><Document /></el-icon>
              </div>
              <div class="feature-card-content">
                <h4 class="feature-card-title">文档解读</h4>
                <p class="feature-card-desc">上传文档，AI智能解读和分析</p>
              </div>
              <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>

        <!-- 会话管理类 -->
        <div class="feature-category">
          <h3 class="category-title">
            <el-icon class="category-icon"><Clock /></el-icon>
            <span>会话管理</span>
          </h3>
          <div class="feature-cards">
            <div 
              class="feature-card" 
              :class="{ 'navigating': isNavigating }"
              @click="$emit('feature-click', 'chat-history')"
              role="button"
              tabindex="0"
              @keydown.enter="$emit('feature-click', 'chat-history')"
            >
              <div class="feature-card-icon">
                <el-icon class="feature-icon"><Clock /></el-icon>
              </div>
              <div class="feature-card-content">
                <h4 class="feature-card-title">对话历史</h4>
                <p class="feature-card-desc">查看和管理历史对话记录</p>
              </div>
              <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Service, Grid, Picture, Folder, Search, Document, Clock, ArrowRight } from '@element-plus/icons-vue'

defineProps({
  isNavigating: {
    type: Boolean,
    default: false
  }
})

defineEmits(['feature-click'])
</script>

<style scoped>
/* 从 Portal.vue 复制相关样式 */
.features-view {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--spacing-xl, 24px);
}

.feature-entries {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl, 24px);
}

.feature-header {
  text-align: center;
  margin-bottom: var(--spacing-lg, 20px);
}

.feature-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm, 8px);
  margin-bottom: var(--spacing-sm, 8px);
}

.feature-title-icon {
  font-size: 32px;
  color: var(--el-color-primary, #409eff);
}

.feature-title-text {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.feature-subtitle {
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
  margin: 0;
}

.feature-categories {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl, 24px);
}

.feature-category {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md, 16px);
}

.category-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm, 8px);
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin: 0;
}

.category-icon {
  font-size: 24px;
  color: var(--el-color-primary, #409eff);
}

.feature-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--spacing-md, 16px);
}

.feature-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md, 16px);
  padding: var(--spacing-lg, 20px);
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: var(--el-border-radius-base, 4px);
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.feature-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  transition: left 0.5s;
}

.feature-card:hover::before {
  left: 100%;
}

.feature-card:hover {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
  transform: translateY(-2px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
}

.feature-card.navigating {
  opacity: 0.6;
  pointer-events: none;
}

.feature-card-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--el-border-radius-base, 4px);
  background: linear-gradient(135deg, var(--el-color-primary-light-8, #d9ecff) 0%, var(--el-color-primary-light-7, #b3d8ff) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-icon {
  font-size: 28px;
  color: var(--el-color-primary, #409eff);
}

.feature-card-content {
  flex: 1;
  min-width: 0;
}

.feature-card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin: 0 0 var(--spacing-xs, 4px) 0;
}

.feature-card-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
  margin: 0;
  line-height: 1.5;
}

.feature-card-arrow {
  font-size: 20px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  flex-shrink: 0;
  transition: all 0.3s;
}

.feature-card:hover .feature-card-arrow {
  color: var(--el-color-primary, #409eff);
  transform: translateX(4px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .feature-cards {
    grid-template-columns: 1fr;
  }
}
</style>
