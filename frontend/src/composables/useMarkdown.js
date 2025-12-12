/**
 * Markdown 渲染 Composables
 * 统一处理 Markdown 渲染逻辑
 */
import { marked } from 'marked'
import hljs from 'highlight.js'
import '@/styles/vscode-dark.css' // VS Code Dark+ 主题
import katex from 'katex'
import 'katex/dist/katex.css'
import mermaid from 'mermaid'

// 配置 marked
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (err) {
        console.error('代码高亮失败:', err)
      }
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true
})

// 初始化 mermaid
mermaid.initialize({ 
  startOnLoad: false,
  theme: 'default'
})

/**
 * 渲染 Markdown 内容
 * @param {string} content Markdown 文本
 * @returns {string} HTML 字符串
 */
export function renderMarkdown(content) {
  if (!content) return ''

  try {
    // 预处理：处理数学公式（必须在 Markdown 渲染之前处理）
    // 使用占位符避免被 Markdown 解析器处理
    const formulaMatches = []
    const blockPlaceholder = '___KATEX_BLOCK_PLACEHOLDER___'
    
    // 先标记块级公式 $$...$$（必须优先处理，避免被行内公式匹配）
    let processedContent = content.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
      const index = formulaMatches.length
      formulaMatches.push({ 
        type: 'block', 
        original: match, 
        formula: formula.trim() 
      })
      return blockPlaceholder + index + blockPlaceholder
    })
    
    // 处理括号格式的公式：( ... )（如果包含 LaTeX 命令）
    // 必须在处理 $...$ 之前，避免冲突
    processedContent = processedContent.replace(/\(([^)]+?)\)/g, (match, formula) => {
      // 如果已经被处理过（包含占位符），跳过
      if (match.includes(blockPlaceholder)) return match
      
      // 检查是否包含 LaTeX 命令（如 \times, \ldots, \sum, \frac 等）
      if (/\\[a-zA-Z]+/.test(formula)) {
        const trimmed = formula.trim()
        if (!trimmed) return match
        
        try {
          return katex.renderToString(trimmed, {
            displayMode: false,
            throwOnError: false
          })
        } catch (e) {
          // 如果渲染失败，返回原文本
          return match
        }
      }
      return match
    })
    
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
        console.warn('KaTeX 渲染失败（行内）:', e, '公式:', formula)
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
          console.warn('KaTeX 渲染失败（块级）:', e, '公式:', blockMatch.formula)
          return blockMatch.original
        }
      }
    )

    // 渲染 Markdown
    let html = marked.parse(processedContent)

    // 处理 mermaid 图表
    html = html.replace(/```mermaid\n([\s\S]*?)\n```/g, (match, code) => {
      const id = `mermaid-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
      // 延迟渲染 mermaid，避免阻塞
      setTimeout(() => {
        const element = document.getElementById(id)
        if (element) {
          mermaid.render(id, code).then(result => {
            element.innerHTML = result.svg
          }).catch(err => {
            console.error('Mermaid 渲染失败:', err)
            element.innerHTML = `<pre>${code}</pre>`
          })
        }
      }, 0)
      return `<div id="${id}" class="mermaid-container">正在渲染图表...</div>`
    })

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
    console.error('Markdown 渲染失败:', error)
    return `<pre>${content}</pre>`
  }
}

/**
 * 清理 Markdown 渲染相关的资源
 */
export function cleanupMarkdown() {
  // 可以在这里清理 mermaid 实例等
}

