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
        console.warn('KaTeX 渲染失败（方括号块级公式）:', e, '公式:', pair.formula)
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
        console.warn('KaTeX 渲染失败（括号公式）:', e, '公式:', pair.formula)
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

    // 为代码块添加 hljs 类（marked 不会自动添加）
    // 处理 <pre><code> 结构，为 <code> 元素添加 hljs 类
    // 使用更宽松的匹配模式，处理 <pre> 和 <code> 之间可能有换行符和空白的情况
    html = html.replace(/<pre[^>]*>\s*<code(?:\s+class="([^"]*)")?[^>]*>/g, (match, existingClass) => {
      if (existingClass) {
        // 如果已有 class 属性，检查是否已包含 hljs
        if (existingClass.includes('hljs')) {
          return match // 已包含 hljs，不需要添加
        }
        // 添加 hljs 到现有类名
        return match.replace(/class="([^"]*)"/, `class="$1 hljs"`)
      } else {
        // 没有 class 属性，直接添加
        return match.replace(/<code([^>]*)>/, '<code$1 class="hljs">')
      }
    })

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


