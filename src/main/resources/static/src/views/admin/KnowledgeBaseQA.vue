<template>
  <div class="knowledge-base-qa">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识库问答</span>
          <el-button type="primary" @click="handleClearHistory">
            <el-icon><Delete /></el-icon>
            清空历史
          </el-button>
        </div>
      </template>

      <div class="qa-container">
        <!-- 左侧：知识库选择 -->
        <div class="left-panel">
          <div class="panel-title">
            <el-icon><Folder /></el-icon>
            <span>选择知识库</span>
          </div>
          <div class="kb-list">
            <div
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :class="['kb-item', { active: selectedKB?.id === kb.id }]"
              @click="selectKB(kb)"
            >
              <el-icon class="kb-icon"><Document /></el-icon>
              <div class="kb-info">
                <div class="kb-name">{{ kb.name }}</div>
                <div class="kb-docs">{{ kb.documentCount }} 个文档</div>
              </div>
              <el-tag
                v-if="kb.status === 'active'"
                type="success"
                size="small"
                class="kb-status"
              >
                启用
              </el-tag>
            </div>
          </div>
        </div>

        <!-- 右侧：问答区域 -->
        <div class="right-panel">
          <div v-if="!selectedKB" class="empty-state">
            <el-icon class="empty-icon"><ChatLineRound /></el-icon>
            <p>请选择一个知识库开始问答</p>
          </div>

          <div v-else class="qa-content">
            <!-- 知识库信息 -->
            <div class="kb-header">
              <div class="kb-header-info">
                <el-icon class="kb-header-icon"><Document /></el-icon>
                <div>
                  <div class="kb-header-name">{{ selectedKB.name }}</div>
                  <div class="kb-header-desc">{{ selectedKB.description }}</div>
                </div>
              </div>
            </div>

            <!-- 对话历史 -->
            <div class="chat-history" ref="chatHistoryRef">
              <div
                v-for="(message, index) in chatHistory"
                :key="index"
                :class="['message-item', message.type]"
              >
                <div class="message-avatar">
                  <el-icon v-if="message.type === 'user'"><User /></el-icon>
                  <el-icon v-else><Service /></el-icon>
                </div>
                <div class="message-content">
                  <div class="message-text">{{ message.content }}</div>
                  <div class="message-time">{{ message.time }}</div>
                </div>
              </div>
            </div>

            <!-- 输入区域 -->
            <div class="input-area">
              <el-input
                v-model="question"
                type="textarea"
                :rows="3"
                placeholder="请输入您的问题..."
                @keydown.ctrl.enter="handleSend"
                :disabled="!selectedKB"
              />
              <div class="input-actions">
                <div class="input-tips">
                  <span>按 Ctrl + Enter 发送</span>
                </div>
                <el-button
                  type="primary"
                  :disabled="!question.trim() || !selectedKB"
                  @click="handleSend"
                  :loading="sending"
                >
                  <el-icon><Promotion /></el-icon>
                  发送
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Delete,
  Folder,
  Document,
  ChatLineRound,
  User,
  Service,
  Promotion
} from '@element-plus/icons-vue'

// 模拟知识库数据
const knowledgeBases = ref([
  {
    id: 1,
    name: '产品使用手册',
    description: '包含产品功能说明、操作指南、常见问题等',
    documentCount: 15,
    status: 'active'
  },
  {
    id: 2,
    name: '技术文档库',
    description: 'API文档、开发指南、架构设计等技术相关文档',
    documentCount: 32,
    status: 'active'
  },
  {
    id: 3,
    name: '客户服务知识库',
    description: '客户常见问题、服务流程、政策说明等',
    documentCount: 28,
    status: 'active'
  }
])

const selectedKB = ref(null)
const question = ref('')
const sending = ref(false)
const chatHistory = ref([])
const chatHistoryRef = ref(null)

// 示例对话历史
const exampleHistory = [
  {
    type: 'user',
    content: '如何使用这个系统？',
    time: '10:30'
  },
  {
    type: 'assistant',
    content: '根据知识库中的文档，系统使用步骤如下：\n1. 首先登录系统\n2. 选择相应的功能模块\n3. 按照界面提示进行操作\n\n如需更详细的说明，请查看产品使用手册。',
    time: '10:31'
  },
  {
    type: 'user',
    content: '如何重置密码？',
    time: '10:35'
  },
  {
    type: 'assistant',
    content: '重置密码的方法：\n1. 点击登录页面的"忘记密码"链接\n2. 输入您的用户名或邮箱\n3. 按照邮件提示完成密码重置\n\n如果遇到问题，请联系管理员。',
    time: '10:35'
  }
]

const selectKB = (kb) => {
  selectedKB.value = kb
  // 切换知识库时，可以加载该知识库的历史对话
  chatHistory.value = [...exampleHistory]
  scrollToBottom()
}

const handleSend = async () => {
  if (!question.value.trim() || !selectedKB.value) {
    return
  }

  const userQuestion = question.value.trim()
  
  // 添加用户消息
  chatHistory.value.push({
    type: 'user',
    content: userQuestion,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  })

  // 清空输入框
  question.value = ''
  sending.value = true

  // 滚动到底部
  await nextTick()
  scrollToBottom()

  // 模拟AI回复（延迟1-2秒）
  setTimeout(() => {
    const responses = [
      `根据知识库"${selectedKB.value.name}"中的信息，${userQuestion}的相关内容如下：\n\n这是一个示例回答。在实际应用中，这里会调用知识库检索API，返回最相关的文档片段作为答案。`,
      `关于"${userQuestion}"，知识库中有以下相关内容：\n\n1. 相关功能说明\n2. 操作步骤\n3. 注意事项\n\n建议您查看详细文档获取更多信息。`,
      `根据知识库检索，关于"${userQuestion}"的回答：\n\n这是一个基于知识库的智能回答。系统会从${selectedKB.value.documentCount}个文档中检索最相关的信息，并生成准确的答案。`
    ]
    
    const randomResponse = responses[Math.floor(Math.random() * responses.length)]
    
    chatHistory.value.push({
      type: 'assistant',
      content: randomResponse,
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    })

    sending.value = false
    scrollToBottom()
  }, 1500)
}

const handleClearHistory = () => {
  if (chatHistory.value.length === 0) {
    ElMessage.info('当前没有对话历史')
    return
  }
  
  chatHistory.value = []
  ElMessage.success('已清空对话历史')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  // 默认选择第一个知识库
  if (knowledgeBases.value.length > 0) {
    selectKB(knowledgeBases.value[0])
  }
})
</script>

<style scoped>
.knowledge-base-qa {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.qa-container {
  display: flex;
  gap: 20px;
  min-height: 600px;
}

.left-panel {
  width: 280px;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 16px;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid transparent;
}

.kb-item:hover {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.kb-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.kb-icon {
  color: #409eff;
  font-size: 20px;
  flex-shrink: 0;
}

.kb-info {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-docs {
  font-size: 12px;
  color: #909399;
}

.kb-status {
  flex-shrink: 0;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 4px;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  color: #c0c4cc;
}

.qa-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.kb-header {
  padding: 16px 20px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.kb-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kb-header-icon {
  font-size: 24px;
  color: #409eff;
  flex-shrink: 0;
}

.kb-header-name {
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.kb-header-desc {
  font-size: 12px;
  color: #909399;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-item {
  display: flex;
  gap: 12px;
  animation: fadeIn 0.3s;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.message-item.user .message-avatar {
  background: #409eff;
  color: white;
}

.message-item.assistant .message-avatar {
  background: #f0f2f5;
  color: #606266;
}

.message-content {
  max-width: 70%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-item.user .message-content {
  align-items: flex-end;
}

.message-text {
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-item.user .message-text {
  background: #409eff;
  color: white;
  border-bottom-right-radius: 2px;
}

.message-item.assistant .message-text {
  background: #f0f2f5;
  color: #303133;
  border-bottom-left-radius: 2px;
}

.message-time {
  font-size: 12px;
  color: #909399;
  padding: 0 4px;
}

.input-area {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-tips {
  font-size: 12px;
  color: #909399;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

