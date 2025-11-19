<template>
  <div class="chat-app">
    <el-card class="chat-container">
      <template #header>
        <div class="chat-header">
          <h3>{{ appInfo?.name || '聊天应用' }}</h3>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <div class="chat-messages" ref="messagesRef">
        <div
          v-for="(message, index) in messages"
          :key="index"
          :class="['message', message.role]"
        >
          <div class="message-content">
            <div class="message-text" v-html="formatMessage(message.content)"></div>
            <div class="message-time">{{ formatTime(message.time) }}</div>
          </div>
        </div>
        <div v-if="loading" class="message assistant">
          <div class="message-content">
            <div class="message-text">正在思考...</div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          placeholder="请输入消息..."
          @keydown.ctrl.enter="handleSend"
        />
        <div class="input-actions">
          <el-button @click="handleClear">清空</el-button>
          <el-button type="primary" @click="handleSend" :loading="loading">
            发送 (Ctrl+Enter)
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAppDetail, chatApp, chatAppStream } from '@/api/aiApp'

const route = useRoute()
const router = useRouter()
const appInfo = ref(null)
const inputText = ref('')
const messages = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const conversationId = ref(null)

const fetchAppInfo = async () => {
  try {
    const res = await getAppDetail(route.params.id)
    appInfo.value = res
  } catch (error) {
    ElMessage.error('获取应用信息失败')
  }
}

const handleSend = async () => {
  if (!inputText.value.trim()) {
    ElMessage.warning('请输入消息')
    return
  }

  const userMessage = {
    role: 'user',
    content: inputText.value,
    time: new Date()
  }
  messages.value.push(userMessage)
  const question = inputText.value
  inputText.value = ''
  loading.value = true

  await nextTick()
  scrollToBottom()

  try {
    const requestData = {
      query: question,
      userId: 'user_' + Date.now(),
      conversationId: conversationId.value,
      stream: appInfo.value?.streamEnabled || false
    }

    if (appInfo.value?.streamEnabled) {
      await handleStreamChat(requestData)
    } else {
      await handleNormalChat(requestData)
    }
  } catch (error) {
    ElMessage.error('发送消息失败')
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发生了错误，请稍后重试。',
      time: new Date()
    })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

const handleNormalChat = async (requestData) => {
  const res = await chatApp(route.params.id, requestData)
  if (res.conversationId) {
    conversationId.value = res.conversationId
  }
  messages.value.push({
    role: 'assistant',
    content: res.answer || '无响应',
    time: new Date()
  })
}

const handleStreamChat = async (requestData) => {
  // 流式响应处理
  const assistantMessage = {
    role: 'assistant',
    content: '',
    time: new Date()
  }
  messages.value.push(assistantMessage)

  try {
    // 这里需要使用EventSource或fetch来处理SSE流
    const response = await fetch(`/api/ai-apps/${route.params.id}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestData)
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value)
      const lines = chunk.split('\n')

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.substring(6)
          if (data === '[DONE]') continue

          try {
            const json = JSON.parse(data)
            if (json.answer) {
              assistantMessage.content += json.answer
              await nextTick()
              scrollToBottom()
            }
            if (json.conversationId) {
              conversationId.value = json.conversationId
            }
          } catch (e) {
            // 忽略解析错误
          }
        }
      }
    }
  } catch (error) {
    throw error
  }
}

const handleClear = () => {
  messages.value = []
  conversationId.value = null
}

const handleBack = () => {
  router.push('/admin/apps')
}

const formatMessage = (content) => {
  if (!content) return ''
  return content.replace(/\n/g, '<br>')
}

const formatTime = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleTimeString('zh-CN')
}

const scrollToBottom = () => {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

onMounted(() => {
  fetchAppInfo()
})
</script>

<style scoped>
.chat-app {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.chat-container {
  width: 100%;
  max-width: 1200px;
  height: 90vh;
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h3 {
  margin: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}

.message {
  margin-bottom: 20px;
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 8px;
}

.message.user .message-content {
  background: #409eff;
  color: white;
}

.message.assistant .message-content {
  background: white;
  color: #333;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.message-text {
  word-wrap: break-word;
  margin-bottom: 4px;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 4px;
}

.chat-input {
  padding: 20px;
  border-top: 1px solid #e4e7ed;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
  gap: 10px;
}
</style>

