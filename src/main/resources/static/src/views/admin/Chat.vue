<template>
  <div class="chat">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>智能问答</span>
          <div class="header-actions">
            <el-checkbox v-model="useStream" size="small">流式响应</el-checkbox>
            <el-button type="primary" @click="handleClearHistory">
              <el-icon><Delete /></el-icon>
              清空历史
            </el-button>
          </div>
        </div>
      </template>

      <div class="chat-container">
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
              <div 
                v-if="message.isLoading"
                class="message-text loading"
              >
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>AI正在思考中...</span>
              </div>
              <div 
                v-else
                class="message-text" 
                v-html="renderMarkdown(message.content)"
                :key="`content-${index}-${message.content.length}`"
              ></div>
              
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
            @keydown.enter.exact.prevent="handleSend"
            :disabled="sending"
          />
          <div class="input-actions">
            <div class="input-tips">
              <span class="tips-text">按 Ctrl + Enter 或 Enter 发送</span>
            </div>
            <el-button
              type="primary"
              :disabled="!question.trim() || sending"
              @click="handleSend"
              :loading="sending"
            >
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Delete,
  ChatLineRound,
  User,
  Service,
  Promotion,
  Loading
} from '@element-plus/icons-vue'
import { chat, chatStream } from '@/api/chat'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import katex from 'katex'
import 'katex/dist/katex.min.css'

// 主流开发语言别名映射
const languageAliases = {
  'js': 'javascript',
  'ts': 'typescript',
  'py': 'python',
  'rb': 'ruby',
  'sh': 'bash',
  'yml': 'yaml',
  'md': 'markdown',
  'json': 'json',
  'xml': 'xml',
  'html': 'html',
  'css': 'css',
  'scss': 'scss',
  'less': 'less',
  'vue': 'vue',
  'react': 'jsx',
  'jsx': 'jsx',
  'tsx': 'tsx',
  'go': 'go',
  'java': 'java',
  'c': 'c',
  'cpp': 'cpp',
  'cs': 'csharp',
  'php': 'php',
  'swift': 'swift',
  'kt': 'kotlin',
  'rs': 'rust',
  'sql': 'sql',
  'dockerfile': 'dockerfile',
  'yaml': 'yaml'
}

// 规范化语言标识符
const normalizeLanguage = (lang) => {
  if (!lang) return null
  const normalized = lang.toLowerCase().trim()
  return languageAliases[normalized] || normalized
}

// 配置 marked - 使用更可靠的代码高亮方式
marked.setOptions({
  highlight: function(code, lang) {
    if (!code) return ''
    
    // 规范化语言标识符
    const normalizedLang = normalizeLanguage(lang)
    
    try {
      let result
      
      // 如果指定了语言且支持，使用指定语言高亮
      if (normalizedLang && hljs.getLanguage(normalizedLang)) {
        try {
          const highlighted = hljs.highlight(code, { language: normalizedLang })
          result = highlighted.value
          console.debug('代码高亮成功', { lang, normalizedLang, codeLength: code.length, resultLength: result.length })
        } catch (err) {
          console.warn('代码高亮失败', err, '语言:', normalizedLang)
          // 如果指定语言失败，尝试自动检测
          result = hljs.highlightAuto(code).value
        }
      } else {
        // 自动检测语言
        const autoResult = hljs.highlightAuto(code, ['javascript', 'typescript', 'python', 'java', 'go', 'rust', 'cpp', 'c', 'csharp', 'php', 'ruby', 'swift', 'kotlin', 'sql', 'html', 'css', 'json', 'xml', 'yaml', 'bash', 'shell'])
        result = autoResult.value
        console.debug('自动检测语言高亮', { detectedLanguage: autoResult.language, codeLength: code.length })
      }
      
      // highlight.js返回的HTML是纯内容，包含带类的span标签
      // 例如：<span class="hljs-keyword">public</span> <span class="hljs-type">int</span>
      // marked会自动将其包装在<pre><code class="hljs">中
      // 检查返回的HTML是否包含高亮标签
      if (result.includes('class="hljs-')) {
        console.debug('highlight.js返回的HTML包含高亮标签，前200字符:', result.substring(0, 200))
      } else {
        console.warn('highlight.js返回的HTML可能没有高亮标签', { result: result.substring(0, 100) })
      }
      
      return result
    } catch (err) {
      console.error('代码高亮异常', err)
      try {
        return hljs.highlightAuto(code).value
      } catch (fallbackErr) {
        console.error('代码高亮降级失败', fallbackErr)
        // 最后的降级：返回原始代码，但添加基本样式
        return `<code class="hljs">${code}</code>`
      }
    }
  },
  breaks: true, // 支持换行
  gfm: true, // 启用 GitHub Flavored Markdown
  headerIds: false, // 禁用自动生成 header IDs
  mangle: false // 禁用邮箱地址混淆
})

const question = ref('')
const sending = ref(false)
const chatHistory = ref([])
const chatHistoryRef = ref(null)
const conversationId = ref(null)
const useStream = ref(true) // 默认使用流式响应
const currentStreamingMessage = ref(null) // 当前正在流式接收的消息索引

// 获取用户信息
const getUserInfo = () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      return JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
  return null
}

const handleSend = async () => {
  if (!question.value.trim() || sending.value) {
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
  
  // 滚动到底部
  await nextTick()
  scrollToBottom()

  // 添加AI回复占位
  const aiMessageIndex = chatHistory.value.length
  chatHistory.value.push({
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: true
  })
  
  currentStreamingMessage.value = aiMessageIndex
  sending.value = true

  // 构建历史对话
  const history = chatHistory.value
    .slice(0, -1) // 排除当前占位的AI消息
    .filter(msg => !msg.isLoading)
    .map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

  const userInfo = getUserInfo()
  const userId = userInfo ? userInfo.userId : null

  try {
    // 确保 conversationId 正确传递（null 或字符串）
    const currentConversationId = conversationId.value
    console.log('发送消息，当前 conversationId:', currentConversationId)
    
    if (useStream.value) {
      // 流式响应
      await handleStreamResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex)
    } else {
      // 非流式响应
      await handleNormalResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex)
    }
  } catch (error) {
    console.error('发送消息失败', error)
    ElMessage.error('发送消息失败：' + (error.message || '未知错误'))
    // 移除失败的AI消息
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = '抱歉，生成答案时发生错误，请重试。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
  } finally {
    sending.value = false
    currentStreamingMessage.value = null
    await nextTick()
    scrollToBottom()
  }
}

// 处理流式响应
const handleStreamResponse = async (question, requestConversationId, userId, history, aiMessageIndex) => {
  let reader = null
  let response = null
  
  try {
    response = await chatStream(question, requestConversationId, userId, history)
    
    if (!response.ok) {
      const errorText = await response.text().catch(() => '未知错误')
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    if (!response.body) {
      throw new Error('响应体不可用，连接可能已断开')
    }

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let hasReceivedData = false

    try {
      while (true) {
        let readResult
        try {
          readResult = await reader.read()
        } catch (readError) {
          // 连接断开错误
          if (readError.message && readError.message.includes('disconnected')) {
            console.warn('流式连接已断开')
            // 如果已经收到部分数据，保留它
            if (hasReceivedData && chatHistory.value[aiMessageIndex].content) {
              chatHistory.value[aiMessageIndex].isLoading = false
              break
            }
            throw new Error('连接已断开，请重试')
          }
          throw readError
        }
        
        const { done, value } = readResult
        
        if (done) {
          break
        }

        hasReceivedData = true
        buffer += decoder.decode(value, { stream: true })
        
        // 处理SSE格式：每行以data:开头，空行分隔
        // 保留最后一行（可能不完整）到buffer中
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // 保留最后一行（可能不完整）

        for (const line of lines) {
          const trimmedLine = line.trim()
          
          // 跳过空行
          if (!trimmedLine) continue
          
          // 处理SSE数据行：data: {...} 或 data:{...}
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            
            // 跳过结束标记和空数据
            if (data === '[DONE]' || data === '') continue

            try {
              const json = JSON.parse(data)
              
              // 更新会话ID（如果响应中包含 conversationId，立即更新，不等待 finished）
              if (json.conversationId) {
                conversationId.value = json.conversationId.toString()
                console.log('流式响应中更新 conversationId:', conversationId.value)
              }
              
              // 更新答案内容（后端已经累积，直接使用）
              if (json.answer !== undefined && json.answer !== null) {
                // 检查消息对象是否存在
                if (!chatHistory.value[aiMessageIndex]) {
                  console.warn('消息对象不存在，索引:', aiMessageIndex, '数组长度:', chatHistory.value.length)
                  continue
                }
                chatHistory.value[aiMessageIndex].content = json.answer
                // 清除加载状态，显示实际内容
                if (chatHistory.value[aiMessageIndex].isLoading) {
                  chatHistory.value[aiMessageIndex].isLoading = false
                }
                
                // 实时滚动（Vue会自动重新渲染Markdown，包括代码高亮）
                await nextTick()
                // 手动触发代码高亮（确保流式响应中的代码块被正确高亮）
                highlightCodeBlocks()
                scrollToBottom()
              }

              // 流式响应完成
              if (json.finished) {
                // 检查消息对象是否存在
                if (chatHistory.value[aiMessageIndex]) {
                  chatHistory.value[aiMessageIndex].isLoading = false
                  chatHistory.value[aiMessageIndex].content = json.answer || chatHistory.value[aiMessageIndex].content || ''
                }
                // 确保会话ID已更新（如果之前没有更新）
                if (json.conversationId) {
                  conversationId.value = json.conversationId.toString()
                  console.log('流式响应完成，最终 conversationId:', conversationId.value)
                }
                // 最终确保代码块被高亮
                await nextTick()
                highlightCodeBlocks()
                break
              }
            } catch (e) {
              console.warn('解析流式数据失败', e, '原始数据:', data)
            }
          }
          // 其他SSE行（如event:、id:等）可以忽略
        }
      }
    } finally {
      // 确保释放reader
      if (reader) {
        try {
          await reader.cancel()
        } catch (cancelError) {
          console.warn('取消reader失败', cancelError)
        }
      }
    }

    // 处理剩余的buffer
    if (buffer.trim()) {
      const lines = buffer.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue
        
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          if (data === '[DONE]' || data === '') continue
          
          try {
            const json = JSON.parse(data)
            if (json.answer !== undefined && json.answer !== null && chatHistory.value[aiMessageIndex]) {
              chatHistory.value[aiMessageIndex].content = json.answer
              chatHistory.value[aiMessageIndex].isLoading = false
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, '原始数据:', data)
          }
        }
      }
    }

    // 如果连接正常结束但没有收到完成标记，确保清除加载状态
    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].isLoading) {
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
  } catch (error) {
    console.error('流式响应处理失败', error)
    
    // 如果已经收到部分数据，保留它并显示错误提示
    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].content) {
      chatHistory.value[aiMessageIndex].isLoading = false
      chatHistory.value[aiMessageIndex].content += '\n\n⚠️ 连接中断，部分内容可能不完整。'
    } else {
      // 如果没有收到任何数据，显示错误消息
      if (chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = '抱歉，连接已断开，请重试。'
        chatHistory.value[aiMessageIndex].isLoading = false
      }
    }
    
    // 重新抛出错误，让上层处理
    throw error
  }
}

// 处理非流式响应
const handleNormalResponse = async (question, requestConversationId, userId, history, aiMessageIndex) => {
  try {
    const response = await chat(question, requestConversationId, userId, history)
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = response.answer || '抱歉，未能生成答案。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
    // 更新会话ID
    if (response.conversationId) {
      conversationId.value = response.conversationId.toString()
      console.log('非流式响应完成，更新 conversationId:', conversationId.value)
    }
    
    // 触发代码高亮
    await nextTick()
    highlightCodeBlocks()
    scrollToBottom()
  } catch (error) {
    console.error('非流式响应处理失败', error)
    throw error
  }
}

// 渲染Markdown
const renderMarkdown = (content) => {
  if (!content) return ''
  
  try {
    // 先预处理公式，在 Markdown 渲染之前标记公式
    // 使用 HTML 注释作为占位符，这样不会被 Markdown 解析器处理
    const formulaMatches = []
    
    // 标记块级公式 $$...$$
    content = content.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
      const index = formulaMatches.length
      formulaMatches.push({ type: 'block', original: match, formula: formula.trim() })
      return `<!--KATEX_FORMULA_${index}-->`
    })
    
    // 标记块级公式 [...]（独立成行）
    content = content.replace(/(?:^|\n)\s*\[([\s\S]*?)\]\s*(?:\n|$)/g, (match, formula) => {
      // 检查是否包含 LaTeX 命令
      if (/\\[a-zA-Z]+/.test(formula)) {
        const index = formulaMatches.length
        formulaMatches.push({ type: 'block', original: match.trim(), formula: formula.trim() })
        return `\n<!--KATEX_FORMULA_${index}-->\n`
      }
      return match
    })
    
    // 标记行内公式 $...$
    content = content.replace(/\$([^$\n]+?)\$/g, (match, formula) => {
      // 如果已经被处理过，跳过
      if (match.includes('<!--KATEX_FORMULA_')) return match
      const index = formulaMatches.length
      formulaMatches.push({ type: 'inline', original: match, formula: formula.trim() })
      return `<!--KATEX_FORMULA_${index}-->`
    })
    
    // 标记行内的 [ ... ] 格式公式
    content = content.replace(/\[([\s\S]*?)\]/g, (match, formula) => {
      // 如果已经被处理过，跳过
      if (match.includes('<!--KATEX_FORMULA_')) return match
      // 检查是否包含 LaTeX 命令
      if (/\\[a-zA-Z]+/.test(formula)) {
        const index = formulaMatches.length
        formulaMatches.push({ type: 'block', original: match, formula: formula.trim() })
        return `<!--KATEX_FORMULA_${index}-->`
      }
      return match
    })
    
    // 渲染 Markdown（包含代码高亮）
    // marked.setOptions 已经配置了 highlight 函数，直接使用 parse
    let html = marked.parse(content)
    
    // 检查是否有公式占位符被误识别为代码块，如果是则恢复
    // 这种情况可能发生在公式格式被 Markdown 解析器识别为代码块时
    html = html.replace(/<pre><code[^>]*>([\s\S]*?)<!--KATEX_FORMULA_(\d+)-->([\s\S]*?)<\/code><\/pre>/g, (match, before, index, after) => {
      // 如果代码块中包含公式占位符，说明公式被误识别为代码块
      // 移除代码块标签，保留占位符
      return (before || '') + `<!--KATEX_FORMULA_${index}-->` + (after || '')
    })
    
    // 确保代码块有正确的类名（hljs）
    html = html.replace(/<pre><code(?!\s+class)/g, '<pre><code class="hljs"')
    html = html.replace(/<pre><code class="language-(\w+)(?!.*hljs)"/g, '<pre><code class="hljs language-$1"')
    html = html.replace(/<pre><code class="([^"]*)"(?!.*hljs)/g, (match, classes) => {
      if (classes && !classes.includes('hljs')) {
        return `<pre><code class="hljs ${classes}"`
      }
      return match
    })
    
    // 恢复并渲染公式（使用 HTML 注释占位符）
    formulaMatches.forEach((formulaMatch, index) => {
      const placeholder = `<!--KATEX_FORMULA_${index}-->`
      // 使用全局替换，确保所有匹配都被替换
      const placeholderRegex = new RegExp(placeholder.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')
      
      try {
        if (formulaMatch.formula) {
          const rendered = katex.renderToString(formulaMatch.formula, {
            displayMode: formulaMatch.type === 'block',
            throwOnError: false
          })
          // 如果是块级公式，包装在 div 中以便正确显示
          const finalRendered = formulaMatch.type === 'block' 
            ? `<div class="katex-formula-block">${rendered}</div>` 
            : rendered
          html = html.replace(placeholderRegex, finalRendered)
        } else {
          html = html.replace(placeholderRegex, formulaMatch.original)
        }
      } catch (e) {
        console.warn('KaTeX 渲染失败:', e, '公式:', formulaMatch.formula)
        html = html.replace(placeholderRegex, formulaMatch.original)
      }
    })
    
    // 处理可能遗留的占位符（如果公式没有被识别，但占位符已经存在）
    // 这种情况可能发生在流式响应中，公式内容还没有完全接收，或者后端直接返回了占位符
    // 先尝试从占位符中提取索引，看看是否有对应的公式
    html = html.replace(/<!--KATEX_FORMULA_(\d+)-->/g, (match, indexStr) => {
      const index = parseInt(indexStr)
      // 检查是否有对应的公式
      if (formulaMatches[index]) {
        // 如果有对应的公式，尝试渲染它
        const formulaMatch = formulaMatches[index]
        try {
          if (formulaMatch.formula) {
            const rendered = katex.renderToString(formulaMatch.formula, {
              displayMode: formulaMatch.type === 'block',
              throwOnError: false
            })
            const finalRendered = formulaMatch.type === 'block' 
              ? `<div class="katex-formula-block">${rendered}</div>` 
              : rendered
            return finalRendered
          }
        } catch (e) {
          console.warn('渲染遗留占位符公式失败:', e, '公式:', formulaMatch.formula)
        }
      }
      // 如果没有对应的公式，返回空字符串，避免显示占位符
      console.warn('发现未处理的公式占位符，但没有对应的公式内容:', match)
      return ''
    })
    
    return html
  } catch (error) {
    console.error('Markdown渲染失败', error)
    return content
  }
}


// 手动触发代码高亮（用于流式响应中逐步生成的代码块）
const highlightCodeBlocks = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      // 查找所有代码块（包括没有hljs类的）
      const codeBlocks = chatHistoryRef.value.querySelectorAll('pre code')
      console.debug('找到代码块数量:', codeBlocks.length)
      
      codeBlocks.forEach((block, index) => {
        try {
          // 检查是否是公式（包含 KaTeX 相关元素或公式占位符）
          const isFormula = block.closest('.katex') || 
                           block.closest('.katex-formula-block') ||
                           block.textContent.includes('<!--KATEX_FORMULA_') ||
                           block.parentElement?.classList.contains('katex-formula-block')
          
          if (isFormula) {
            console.debug(`代码块 ${index} 是公式，跳过代码高亮`)
            return
          }
          
          const originalText = block.textContent
          // 如果代码块没有hljs类，或者内容已更新，重新高亮
          const needsHighlight = !block.classList.contains('hljs') || 
                                block.dataset.highlighted !== 'true' ||
                                block.dataset.originalText !== originalText
          
          if (needsHighlight && originalText.trim()) {
            // 获取语言标识符（从class或父元素）
            let lang = block.className.match(/language-(\w+)/)?.[1] || 
                      block.parentElement?.className.match(/language-(\w+)/)?.[1] ||
                      block.getAttribute('data-lang')
            
            // 规范化语言标识符
            const normalizedLang = lang ? normalizeLanguage(lang) : null
            
            let highlightedHtml
            if (normalizedLang && hljs.getLanguage(normalizedLang)) {
              // 使用指定语言高亮
              highlightedHtml = hljs.highlight(originalText, { language: normalizedLang }).value
              console.debug(`代码块 ${index} 使用语言 ${normalizedLang} 高亮成功`)
            } else {
              // 自动检测语言
              const result = hljs.highlightAuto(originalText, ['javascript', 'typescript', 'python', 'java', 'go', 'rust', 'cpp', 'c', 'csharp', 'php', 'ruby', 'swift', 'kotlin', 'sql', 'html', 'css', 'json', 'xml', 'yaml', 'bash', 'shell'])
              highlightedHtml = result.value
              console.debug(`代码块 ${index} 自动检测语言 ${result.language} 高亮成功`)
            }
            
            // 更新HTML并添加类名
            block.innerHTML = highlightedHtml
            block.classList.add('hljs')
            block.dataset.highlighted = 'true'
            block.dataset.originalText = originalText
            
            // 检查是否包含高亮标签
            if (block.innerHTML.includes('class="hljs-')) {
              console.debug(`代码块 ${index} 高亮标签已应用`)
            } else {
              console.warn(`代码块 ${index} 高亮后没有hljs-标签`, block.innerHTML.substring(0, 100))
            }
          }
        } catch (err) {
          console.error('手动高亮代码块失败', err, block)
        }
      })
    }
  })
}

const handleClearHistory = () => {
  if (chatHistory.value.length === 0) {
    ElMessage.info('当前没有对话历史')
    return
  }
  
  chatHistory.value = []
  conversationId.value = null
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
  // 页面加载时滚动到底部
  scrollToBottom()
  // 确保代码高亮正常工作
  highlightCodeBlocks()
})
</script>

<style scoped>
.chat {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: 18px 20px;
}

:deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px;
  padding-bottom: 200px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
}

.chat-history::-webkit-scrollbar {
  width: 6px;
}

.chat-history::-webkit-scrollbar-track {
  background: transparent;
}

.chat-history::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

.chat-history::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
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
  color: #409eff;
}

.message-content {
  flex: 1;
  min-width: 0;
  max-width: 70%;
}

.message-text {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.message-item.user .message-text {
  background: #409eff;
  color: white;
}

.message-text.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  text-align: right;
}

.message-item.user .message-time {
  text-align: left;
}

.input-area {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: white;
  flex-shrink: 0;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-tips {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.tips-text {
  font-size: 12px;
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

/* Markdown样式 */
:deep(.message-text) {
  h1, h2, h3, h4, h5, h6 {
    margin-top: 16px;
    margin-bottom: 8px;
    font-weight: 600;
  }
  
  h1 { font-size: 24px; }
  h2 { font-size: 20px; }
  h3 { font-size: 18px; }
  
  p {
    margin: 8px 0;
  }
  
  ul, ol {
    margin: 8px 0;
    padding-left: 24px;
  }
  
  code {
    background: rgba(0, 0, 0, 0.1);
    padding: 2px 6px;
    border-radius: 3px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    color: #e83e8c;
  }
  
  /* 行内代码样式 */
  p code,
  li code,
  td code {
    background: rgba(0, 0, 0, 0.08);
    padding: 2px 6px;
    border-radius: 3px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    color: #e83e8c;
  }
  
  pre {
    background: #1e1e1e;
    color: #d4d4d4;
    padding: 16px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 12px 0;
    position: relative;
    border: 1px solid rgba(255, 255, 255, 0.1);
  }
  
  pre code {
    background: transparent;
    padding: 0;
    /* 重要：不要设置color，否则会覆盖highlight.js的颜色 */
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px;
    line-height: 1.6;
    display: block;
    width: 100%;
  }
  
  /* 确保代码高亮正常工作 */
  pre code.hljs {
    display: block;
    overflow-x: auto;
    padding: 0;
    background: transparent;
    /* 关键：不设置color，让highlight.js的github-dark主题自己处理所有颜色 */
    /* 如果设置了color，会覆盖highlight.js的关键字颜色 */
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px;
    line-height: 1.6;
  }
  
  /* 确保highlight.js的样式能够正确应用 */
  /* github-dark主题已经包含了所有关键字、字符串、注释等的颜色样式 */
  /* 不要在这里覆盖任何颜色相关的样式 */
  
  code.hljs {
    padding: 2px 6px;
    border-radius: 3px;
  }
  
  blockquote {
    border-left: 4px solid #409eff;
    padding-left: 16px;
    margin: 12px 0;
    color: #606266;
  }
  
  table {
    border-collapse: collapse;
    width: 100%;
    margin: 12px 0;
  }
  
  table th,
  table td {
    border: 1px solid #e4e7ed;
    padding: 8px 12px;
    text-align: left;
  }
  
  table th {
    background: #f5f7fa;
    font-weight: 600;
  }
}

.message-item.user :deep(.message-text) {
  h1, h2, h3, h4, h5, h6,
  p, ul, ol, blockquote {
    color: white;
  }
  
  code {
    background: rgba(255, 255, 255, 0.2);
    color: white;
  }
  
  pre {
    background: rgba(0, 0, 0, 0.4);
    color: #e0e0e0;
    border: 1px solid rgba(255, 255, 255, 0.1);
  }
  
  pre code {
    color: #e0e0e0;
  }
  
  /* 用户消息中的代码高亮 */
  pre code.hljs {
    background: transparent;
    color: #e0e0e0;
  }
  
  table th,
  table td {
    border-color: rgba(255, 255, 255, 0.3);
  }
  
  table th {
    background: rgba(255, 255, 255, 0.1);
  }
}
</style>

