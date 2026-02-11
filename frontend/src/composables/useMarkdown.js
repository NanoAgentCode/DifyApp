/**
 * Markdown 渲染 Composables
 * 统一处理 Markdown 渲染逻辑
 */
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import '@/styles/vscode-dark.css' // VS Code Dark+ 主题
import katex from 'katex'
import 'katex/dist/katex.css'
import { logger } from '@/utils/logger'

// 配置 marked v17 使用新的 API
const marked = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      if (lang && hljs.getLanguage(lang)) {
        try {
          return hljs.highlight(code, { language: lang }).value
        } catch (err) {
          logger.debug('代码高亮失败:', err)
          return hljs.highlightAuto(code).value
        }
      }
      return hljs.highlightAuto(code).value
    }
  })
)

// 设置其他选项
marked.setOptions({
  breaks: true,
  gfm: true
})

/**
 * 查找并替换裸的 \begin{env}...\end{env} 块（如 bmatrix、pmatrix），用占位符替代，供后续 KaTeX 渲染
 */
function replaceBeginEndBlocks(content, blockPlaceholder, formulaMatches) {
  let processed = content
  let searchStart = 0
  while (true) {
    const beginIdx = processed.indexOf('\\begin{', searchStart)
    if (beginIdx === -1) break
    const braceEnd = processed.indexOf('}', beginIdx + 7)
    if (braceEnd === -1) break
    const envName = processed.substring(beginIdx + 7, braceEnd)
    const endTag = '\\end{' + envName + '}'
    const beginTag = '\\begin{' + envName + '}'
    let depth = 1
    let pos = braceEnd + 1
    let endIdx = -1
    while (pos < processed.length && depth > 0) {
      const nextBegin = processed.indexOf(beginTag, pos)
      const nextEnd = processed.indexOf(endTag, pos)
      if (nextEnd === -1) break
      if (nextBegin !== -1 && nextBegin < nextEnd) {
        depth++
        pos = nextBegin + beginTag.length
      } else {
        depth--
        if (depth === 0) {
          endIdx = nextEnd + endTag.length
          break
        }
        pos = nextEnd + endTag.length
      }
    }
    if (endIdx === -1) {
      searchStart = beginIdx + 1
      continue
    }
    const fullBlock = processed.substring(beginIdx, endIdx)
    const index = formulaMatches.length
    formulaMatches.push({ type: 'block', original: fullBlock, formula: fullBlock })
    processed = processed.substring(0, beginIdx) + blockPlaceholder + index + blockPlaceholder + processed.substring(endIdx)
    searchStart = beginIdx + blockPlaceholder.length + String(index).length + blockPlaceholder.length
  }
  return processed
}

/**
 * 渲染 Markdown 内容
 * @param {string} content Markdown 文本
 * @returns {string} HTML 字符串
 */
export function renderMarkdown(content) {
  if (!content) return ''
  
  // 确保 content 是字符串类型
  if (typeof content !== 'string') {
    content = String(content)
  }

  try {
    // 预处理：处理数学公式（必须在 Markdown 渲染之前处理）
    // 使用占位符避免被 Markdown 解析器处理
    const formulaMatches = []
    const blockPlaceholder = '___KATEX_BLOCK_PLACEHOLDER___'
    
    // 先标记块级公式 $$...$$ 和 [ ... ]（必须优先处理，避免被行内公式匹配）
    // 处理 $$...$$ 格式
    let processedContent = content.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
      const index = formulaMatches.length
      formulaMatches.push({ 
        type: 'block', 
        original: match, 
        formula: formula.trim() 
      })
      return blockPlaceholder + index + blockPlaceholder
    })

    // 处理裸的 \begin{env}...\end{env}（如 \begin{bmatrix}、\begin{pmatrix}，未包在 $$ 内）
    processedContent = replaceBeginEndBlocks(processedContent, blockPlaceholder, formulaMatches)
    
    // 处理 [ ... ] 格式的块级公式（支持嵌套方括号）
    const bracketPairs = []
    let bracketPos = 0
    while (bracketPos < processedContent.length) {
      const leftBracketPos = processedContent.indexOf('[', bracketPos)
      if (leftBracketPos === -1) break
      
      // 跳过已经被处理过的位置（包含占位符）
      if (processedContent.substring(Math.max(0, leftBracketPos - 20), leftBracketPos + 20).includes(blockPlaceholder)) {
        bracketPos = leftBracketPos + 1
        continue
      }
      
      // 检查前面是否有反斜杠（如果是转义的，跳过）
      if (leftBracketPos > 0 && processedContent[leftBracketPos - 1] === '\\') {
        bracketPos = leftBracketPos + 1
        continue
      }
      
      // 查找匹配的右方括号
      let depth = 1
      let rightBracketPos = leftBracketPos + 1
      
      while (rightBracketPos < processedContent.length && depth > 0) {
        const char = processedContent[rightBracketPos]
        // 检查是否有转义的反斜杠
        if (char === '\\' && rightBracketPos + 1 < processedContent.length) {
          rightBracketPos += 2
          continue
        }
        if (char === '[') {
          depth++
        } else if (char === ']') {
          depth--
        }
        if (depth > 0) {
          rightBracketPos++
        }
      }
      
      // 如果找到了匹配的右方括号
      if (depth === 0) {
        const formula = processedContent.substring(leftBracketPos + 1, rightBracketPos)
        const trimmed = formula.trim()
        
        // 检查是否包含 LaTeX 命令（如 \times, \ldots, \sum, \frac, \geq, \cdots 等）
        if (trimmed && /\\[a-zA-Z]+/.test(trimmed)) {
          bracketPairs.push({
            start: leftBracketPos,
            end: rightBracketPos + 1,
            formula: trimmed,
            original: `[${formula}]`
          })
        }
        bracketPos = rightBracketPos + 1
      } else {
        bracketPos = leftBracketPos + 1
      }
    }
    
    // 从后往前替换方括号公式，避免位置偏移
    for (let i = bracketPairs.length - 1; i >= 0; i--) {
      const pair = bracketPairs[i]
      try {
        // 清理公式
        let finalFormula = pair.formula.trim()
        
        // 移除 HTML 实体
        finalFormula = finalFormula
          .replace(/&#x27;/g, "'")
          .replace(/&#39;/g, "'")
          .replace(/&quot;/g, '"')
          .replace(/&lt;/g, '<')
          .replace(/&gt;/g, '>')
          .replace(/&amp;/g, '&')
        
        // 移除末尾的反斜杠
        finalFormula = finalFormula.replace(/\\+$/, '')
        
        // 修复多余的转义
        finalFormula = finalFormula.replace(/([^\\])\\\\([^a-zA-Z\\])/g, '$1\\$2')
        finalFormula = finalFormula.trim()
        
        const rendered = katex.renderToString(finalFormula, {
          displayMode: true, // 块级公式使用 displayMode
          throwOnError: false
        })
        const index = formulaMatches.length
        formulaMatches.push({ 
          type: 'block', 
          original: pair.original, 
          formula: finalFormula 
        })
        processedContent = processedContent.substring(0, pair.start) + 
                          blockPlaceholder + index + blockPlaceholder + 
                          processedContent.substring(pair.end)
      } catch (e) {
        logger.debug('KaTeX 渲染失败（方括号块级公式）:', e)
      }
    }
    
    // 处理括号格式的公式：( ... )（如果包含 LaTeX 命令）
    // 必须在处理 $...$ 之前，避免冲突
    // 使用函数来匹配平衡的括号，支持嵌套括号
    const parenPlaceholder = '___KATEX_PAREN_PLACEHOLDER___'
    const parenMatches = []
    
    // 查找所有括号对（从后往前查找，优先处理最外层括号，避免位置偏移）
    const parenPairs = []
    
    // 从后往前查找所有右括号，然后向前查找匹配的左括号
    let searchPos = processedContent.length - 1
    while (searchPos >= 0) {
      const rightParenPos = processedContent.lastIndexOf(')', searchPos)
      if (rightParenPos === -1) break
      
      // 跳过已经被处理过的位置（包含占位符）
      if (processedContent.substring(Math.max(0, rightParenPos - 20), Math.min(processedContent.length, rightParenPos + 20)).includes(blockPlaceholder) ||
          processedContent.substring(Math.max(0, rightParenPos - 20), Math.min(processedContent.length, rightParenPos + 20)).includes(parenPlaceholder)) {
        searchPos = rightParenPos - 1
        continue
      }
      
      // 检查前面是否有反斜杠（如果是转义的，跳过）
      if (rightParenPos > 0 && processedContent[rightParenPos - 1] === '\\') {
        searchPos = rightParenPos - 1
        continue
      }
      
      // 从右括号向前查找匹配的左括号
      let depth = 1
      let leftParenPos = rightParenPos - 1
      
      while (leftParenPos >= 0 && depth > 0) {
        const char = processedContent[leftParenPos]
        // 检查是否有转义的反斜杠
        if (char === '\\' && leftParenPos > 0 && processedContent[leftParenPos - 1] !== '\\') {
          leftParenPos--
          continue
        }
        if (char === ')') {
          depth++
        } else if (char === '(') {
          depth--
        }
        if (depth > 0) {
          leftParenPos--
        }
      }
      
      // 如果找到了匹配的左括号
      if (depth === 0 && leftParenPos >= 0) {
        // 检查前面是否有反斜杠（如果是转义的，跳过）
        if (leftParenPos > 0 && processedContent[leftParenPos - 1] === '\\') {
          searchPos = rightParenPos - 1
          continue
        }
        
        const formula = processedContent.substring(leftParenPos + 1, rightParenPos)
        
        // 清理公式：移除可能的转义字符和多余空白
        let cleanedFormula = formula.trim()
        
        // 移除开头和结尾的反斜杠（可能是转义字符）
        cleanedFormula = cleanedFormula.replace(/^\\+/, '').replace(/\\+$/, '')
        
        // 检查是否包含 LaTeX 命令（如 \times, \ldots, \sum, \frac, \geq 等）
        if (/\\[a-zA-Z]+/.test(cleanedFormula)) {
          if (cleanedFormula) {
            // 检查这个括号对是否已经被其他括号对包含（避免重复处理）
            let isNested = false
            for (const existingPair of parenPairs) {
              if (leftParenPos > existingPair.start && rightParenPos < existingPair.end) {
                isNested = true
                break
              }
            }
            
            if (!isNested) {
              parenPairs.push({
                start: leftParenPos,
                end: rightParenPos + 1,
                formula: cleanedFormula,
                original: `(${formula})`
              })
            }
          }
        }
      }
      
      searchPos = rightParenPos - 1
    }
    
    // 按起始位置排序，确保从后往前替换
    parenPairs.sort((a, b) => b.start - a.start)
    
    // 从后往前替换，避免位置偏移
    for (let i = parenPairs.length - 1; i >= 0; i--) {
      const pair = parenPairs[i]
      try {
        // 再次清理公式，确保没有多余的空白或转义字符
        let finalFormula = pair.formula.trim()
        
        // 移除 HTML 实体（如果存在）
        finalFormula = finalFormula
          .replace(/&#x27;/g, "'")
          .replace(/&#39;/g, "'")
          .replace(/&quot;/g, '"')
          .replace(/&lt;/g, '<')
          .replace(/&gt;/g, '>')
          .replace(/&amp;/g, '&')
        
        // 移除末尾的反斜杠（可能是转义字符）
        finalFormula = finalFormula.replace(/\\+$/, '')
        
        // 确保 LaTeX 命令格式正确
        // 修复可能的转义问题：将 \\ 转换为单个 \（但保留 LaTeX 命令中的 \）
        // 注意：不要替换 LaTeX 命令中的反斜杠，只处理多余的转义
        // 匹配：非反斜杠字符 + 两个反斜杠 + 非字母字符，替换为：非反斜杠字符 + 单个反斜杠 + 非字母字符
        finalFormula = finalFormula.replace(/([^\\])\\\\([^a-zA-Z\\])/g, '$1\\$2')
        
        // 再次 trim，确保没有多余的空白
        finalFormula = finalFormula.trim()
        
        const rendered = katex.renderToString(finalFormula, {
          displayMode: false,
          throwOnError: false
        })
        const placeholder = parenPlaceholder + parenMatches.length + parenPlaceholder
        parenMatches.push({
          original: pair.original,
          rendered: rendered
        })
        processedContent = processedContent.substring(0, pair.start) + 
                          placeholder + 
                          processedContent.substring(pair.end)
      } catch (e) {
        // 如果渲染失败，记录错误但继续处理其他公式
        logger.debug('KaTeX 渲染失败（括号公式）:', e)
      }
    }
    
    // 恢复括号公式占位符
    processedContent = processedContent.replace(
      new RegExp(parenPlaceholder.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '(\\d+)' + parenPlaceholder.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), 
      (match, index) => {
        const parenMatch = parenMatches[parseInt(index)]
        if (parenMatch) {
          return parenMatch.rendered
        }
        return match
      }
    )
    
    // 处理行内公式 $...$（不包含换行符）
    processedContent = processedContent.replace(/\$([^$\n]+?)\$/g, (match, formula) => {
      // 如果已经被处理过（包含占位符），跳过
      if (match.includes(blockPlaceholder)) return match
      
      const trimmed = formula.trim()
      if (!trimmed) return match
      
      try {
        return katex.renderToString(trimmed, {
          displayMode: false,
          throwOnError: false
        })
      } catch (e) {
        logger.debug('KaTeX 渲染失败（行内）:', e)
        return match
      }
    })
    
    // 恢复并渲染块级公式
    processedContent = processedContent.replace(
      new RegExp(blockPlaceholder + '(\\d+)' + blockPlaceholder, 'g'), 
      (match, index) => {
        const blockMatch = formulaMatches[parseInt(index)]
        if (!blockMatch) return match
        
        try {
          if (!blockMatch.formula) return blockMatch.original
          return katex.renderToString(blockMatch.formula, {
            displayMode: true,
            throwOnError: false
          })
        } catch (e) {
          logger.debug('KaTeX 渲染失败（块级）:', e)
          return blockMatch.original
        }
      }
    )

    // 渲染 Markdown
    let html = marked.parse(processedContent)

    // marked-highlight 会自动添加 hljs 类，但为了确保兼容性，检查并确保所有代码块都有 hljs 类
    // 使用 DOM 操作确保可靠性，避免正则表达式匹配问题
    if (typeof document !== 'undefined') {
      // 创建一个临时容器来解析 HTML
      const tempDiv = document.createElement('div')
      tempDiv.innerHTML = html
      
      // 查找所有 <pre><code> 结构
      const preElements = tempDiv.querySelectorAll('pre')
      preElements.forEach(pre => {
        const codeElement = pre.querySelector('code')
        if (codeElement) {
          // 检查是否已有 class 属性
          const existingClass = codeElement.getAttribute('class') || ''
          // 确保包含 hljs 类（marked-highlight 应该已经添加了，但为了保险起见）
          if (!existingClass.includes('hljs')) {
            const newClass = existingClass ? `${existingClass} hljs` : 'hljs'
            codeElement.setAttribute('class', newClass)
          }
        }
      })
      
      // 获取处理后的 HTML
      html = tempDiv.innerHTML
    } else {
      // 如果 document 不可用（SSR 环境），使用正则表达式作为后备方案
      html = html.replace(/<pre[^>]*>\s*<code(?:\s+class="([^"]*)")?[^>]*>/g, (match, existingClass) => {
        if (existingClass) {
          if (existingClass.includes('hljs')) {
            return match
          }
          return match.replace(/class="([^"]*)"/, `class="$1 hljs"`)
        } else {
          return match.replace(/<code([^>]*)>/, '<code$1 class="hljs">')
        }
      })
    }

    // 处理可能被 Markdown 转义的公式（在渲染后再次检查）
    // 处理行内公式的转义情况：\( ... \)
    html = html.replace(/\\\(([^)]+?)\\\)/g, (match, formula) => {
      try {
        return katex.renderToString(formula.trim(), {
          displayMode: false,
          throwOnError: false
        })
      } catch (e) {
        return match
      }
    })
    
    // 处理块级公式的转义情况：\[ ... \]
    html = html.replace(/\\\[([\s\S]*?)\\\]/g, (match, formula) => {
      try {
        return katex.renderToString(formula.trim(), {
          displayMode: true,
          throwOnError: false
        })
      } catch (e) {
        return match
      }
    })

    return html
  } catch (error) {
    logger.error('Markdown 渲染失败:', error)
    return `<pre>${content}</pre>`
  }
}

/**
 * 轻量 Markdown 渲染（流式打字机期间使用）
 * 跳过 KaTeX 公式预处理，仅执行 marked + highlight.js，性能更优
 * @param {string} content Markdown 文本
 * @returns {string} HTML 字符串
 */
export function renderMarkdownLight(content) {
  if (!content) return ''

  if (typeof content !== 'string') {
    content = String(content)
  }

  try {
    let html = marked.parse(content)

    // 确保代码块有 hljs 类（与 renderMarkdown 保持一致）
    if (typeof document !== 'undefined') {
      const tempDiv = document.createElement('div')
      tempDiv.innerHTML = html
      const preElements = tempDiv.querySelectorAll('pre')
      preElements.forEach(pre => {
        const codeElement = pre.querySelector('code')
        if (codeElement) {
          const existingClass = codeElement.getAttribute('class') || ''
          if (!existingClass.includes('hljs')) {
            codeElement.setAttribute('class', existingClass ? `${existingClass} hljs` : 'hljs')
          }
        }
      })
      html = tempDiv.innerHTML
    }

    return html
  } catch (error) {
    logger.error('Markdown 轻量渲染失败:', error)
    return `<pre>${content}</pre>`
  }
}

/**
 * 清理 Markdown 渲染相关的资源
 */
export function cleanupMarkdown() {
  // 可以在这里清理渲染相关的资源
}


