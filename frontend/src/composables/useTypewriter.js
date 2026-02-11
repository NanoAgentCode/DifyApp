/**
 * 打字机效果 Composable
 * 将流式到达的文本逐字释放，实现打字机动画效果
 *
 * 核心思路：
 *   SSE 每次推送累积内容 → feed(fullContent) 更新 targetContent
 *   内部定时器按自适应速度将 targetContent 逐步释放到 displayedContent
 *
 * 自适应速度策略：
 *   缓冲 > 200 字符 → 每 tick 5 字符，10ms 间隔（快速追赶）
 *   缓冲 50-200    → 每 tick 3 字符，18ms 间隔
 *   缓冲 < 50      → 每 tick 1 字符，30ms 间隔（接近实时逐字）
 *
 * 公式安全：
 *   safeDisplayedContent 自动裁剪未闭合的公式分隔符（$$, $, \[, \(）
 *   确保 renderMarkdown 始终处理完整的公式块，公式完整后才一次性渲染
 */
import { ref, computed } from 'vue'

/**
 * @param {Object} options 配置项
 * @param {number} options.fastChunk   缓冲大时每 tick 输出字符数（默认 5）
 * @param {number} options.midChunk    缓冲中等时每 tick 输出字符数（默认 3）
 * @param {number} options.slowChunk   缓冲小时每 tick 输出字符数（默认 1）
 * @param {number} options.fastSpeed   缓冲大时 tick 间隔 ms（默认 10）
 * @param {number} options.midSpeed    缓冲中等时 tick 间隔 ms（默认 18）
 * @param {number} options.slowSpeed   缓冲小时 tick 间隔 ms（默认 30）
 * @param {number} options.fastThreshold  进入快速模式的缓冲阈值（默认 200）
 * @param {number} options.midThreshold   进入中速模式的缓冲阈值（默认 50）
 */
export function useTypewriter(options = {}) {
  const {
    fastChunk = 5,
    midChunk = 3,
    slowChunk = 1,
    fastSpeed = 10,
    midSpeed = 18,
    slowSpeed = 30,
    fastThreshold = 200,
    midThreshold = 50
  } = options

  /** 当前已显示的文本（响应式，供模板绑定） */
  const displayedContent = ref('')
  /** 是否正在逐字输出中 */
  const isTyping = ref(false)

  /**
   * 公式安全的显示内容（计算属性）
   * 自动裁剪末尾未闭合的公式分隔符，防止 renderMarkdown 处理不完整的公式
   * 效果：文本逐字出现，公式等到分隔符闭合后才一次性渲染
   */
  const safeDisplayedContent = computed(() => {
    return trimFormulaDelimiters(displayedContent.value)
  })

  // ---- 内部状态（非响应式，避免不必要的渲染开销） ----
  let targetContent = ''   // SSE 推送的完整累积文本
  let displayIndex = 0     // 已释放到 displayedContent 的字符位置
  let timer = null         // setTimeout id

  /**
   * 接收 SSE 累积内容
   * 每次 SSE onData 时调用，传入当前完整的 answer 文本
   */
  function feed(content) {
    targetContent = content
    // 如果定时器已停（上一轮 tick 追上了旧 target），重新启动
    if (!isTyping.value && displayIndex < targetContent.length) {
      isTyping.value = true
      // 立即执行首次 tick，避免初始空白帧
      tick()
    }
  }

  /**
   * 核心 tick：释放一批字符，然后安排下一次
   */
  function tick() {
    timer = null

    if (displayIndex >= targetContent.length) {
      // 已追上目标，暂停等待新数据
      isTyping.value = false
      return
    }

    // 计算缓冲区大小（未释放的字符数）
    const buffered = targetContent.length - displayIndex

    // 自适应 chunk 和 speed
    let chunk, speed
    if (buffered > fastThreshold) {
      chunk = fastChunk
      speed = fastSpeed
    } else if (buffered > midThreshold) {
      chunk = midChunk
      speed = midSpeed
    } else {
      chunk = slowChunk
      speed = slowSpeed
    }

    // 前进 displayIndex（不超过 target 长度）
    displayIndex = Math.min(displayIndex + chunk, targetContent.length)
    displayedContent.value = targetContent.substring(0, displayIndex)

    // 安排下一次 tick
    scheduleTick(speed)
  }

  /**
   * 安排下一次 tick（如果没有待执行的定时器）
   */
  function scheduleTick(delay) {
    if (timer !== null) return // 已有待执行的 tick，不重复安排
    // 如果未传 delay，使用默认最快速度（feed 触发时）
    timer = setTimeout(tick, delay ?? fastSpeed)
  }

  /**
   * 立即显示所有剩余内容（流结束时调用）
   */
  function finish() {
    clearTimer()
    if (targetContent) {
      displayedContent.value = targetContent
      displayIndex = targetContent.length
    }
    isTyping.value = false
  }

  /**
   * 重置状态（准备接收下一条消息）
   */
  function reset() {
    clearTimer()
    targetContent = ''
    displayedContent.value = ''
    displayIndex = 0
    isTyping.value = false
  }

  /**
   * 清理定时器（组件卸载时调用）
   */
  function destroy() {
    clearTimer()
  }

  // ---- 内部工具 ----
  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  return {
    displayedContent,
    safeDisplayedContent,
    isTyping,
    feed,
    finish,
    reset,
    destroy
  }
}

/**
 * 裁剪未闭合的公式分隔符，确保内容对 KaTeX 渲染安全
 *
 * 支持的分隔符（与 useMarkdown 中 KaTeX 一致）：
 * - $$...$$  块级
 * - $...$    行内
 * - \[...\]  块级（LaTeX 风格）
 * - \(...\)  行内（LaTeX 风格）
 *
 * 若末尾存在任一未闭合分隔符，裁剪到该分隔符之前，公式完整后才一次性渲染
 */
function trimFormulaDelimiters(content) {
  if (!content) return ''

  let i = 0
  let inBlock = false
  let inInline = false
  let inBracket = false   // \[...\]
  let inParen = false     // \(...\)
  let blockStart = -1
  let inlineStart = -1
  let bracketStart = -1
  let parenStart = -1
  /** @type {{ name: string, start: number }[]} */
  const beginStack = []

  while (i < content.length) {
    if (content[i] === '\\' && i + 1 < content.length) {
      const next = content[i + 1]
      if (next === '[') {
        if (!inBracket && !inParen && !inBlock && !inInline && beginStack.length === 0) {
          inBracket = true
          bracketStart = i
        }
        i += 2
        continue
      }
      if (next === ']') {
        if (inBracket) {
          inBracket = false
          bracketStart = -1
        }
        i += 2
        continue
      }
      if (next === '(') {
        if (!inBracket && !inParen && !inBlock && !inInline && beginStack.length === 0) {
          inParen = true
          parenStart = i
        }
        i += 2
        continue
      }
      if (next === ')') {
        if (inParen) {
          inParen = false
          parenStart = -1
        }
        i += 2
        continue
      }
      // \begin{env} 或 \end{env}（如 \begin{bmatrix}、\begin{pmatrix}）
      if (content.substring(i, i + 7) === '\\begin{') {
        const closeBrace = content.indexOf('}', i + 7)
        if (closeBrace !== -1) {
          const envName = content.substring(i + 7, closeBrace)
          beginStack.push({ name: envName, start: i })
          i = closeBrace + 1
          continue
        }
      }
      if (content.substring(i, i + 5) === '\\end{') {
        const closeBrace = content.indexOf('}', i + 5)
        if (closeBrace !== -1 && beginStack.length > 0) {
          const envName = content.substring(i + 5, closeBrace)
          if (beginStack[beginStack.length - 1].name === envName) {
            beginStack.pop()
          }
          i = closeBrace + 1
          continue
        }
      }
    }

    if (content[i] === '$') {
      if (i + 1 < content.length && content[i + 1] === '$') {
        if (inBlock) {
          inBlock = false
          blockStart = -1
        } else if (!inInline && !inBracket && !inParen && beginStack.length === 0) {
          inBlock = true
          blockStart = i
        }
        i += 2
        continue
      }
      if (!inBlock && !inBracket && !inParen) {
        if (inInline) {
          inInline = false
          inlineStart = -1
        } else {
          inInline = true
          inlineStart = i
        }
      }
    }
    i++
  }

  const unclosedStarts = [blockStart, inlineStart, bracketStart, parenStart].filter(p => p >= 0)
  if (beginStack.length > 0) {
    unclosedStarts.push(beginStack[0].start)
  }
  if (unclosedStarts.length > 0) {
    return content.substring(0, Math.min(...unclosedStarts))
  }
  return content
}
