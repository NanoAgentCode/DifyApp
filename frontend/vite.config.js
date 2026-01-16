import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  // 优化导入预加载
  build: {
    target: 'es2015',
    cssTarget: 'chrome80',
    outDir: 'dist',
    assetsDir: 'assets',
    // 代码分割优化
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          // Vue 相关库
          if (id.includes('node_modules/vue/') || 
              id.includes('node_modules/@vue/') ||
              id.includes('node_modules/vue-router/') ||
              id.includes('node_modules/pinia/')) {
            return 'vue-vendor'
          }
          
          // Element Plus 核心库
          if (id.includes('node_modules/element-plus/') && 
              !id.includes('node_modules/@element-plus/')) {
            return 'element-plus-core'
          }
          
          // Element Plus 图标库单独打包
          if (id.includes('node_modules/@element-plus/icons-vue/')) {
            return 'element-plus-icons'
          }
          
          // Mermaid 图表库单独打包
          if (id.includes('node_modules/mermaid/')) {
            return 'mermaid'
          }
          
          // Markdown 相关库 - 分别打包
          if (id.includes('node_modules/marked/') || id.includes('node_modules/marked-highlight/')) {
            return 'marked'
          }
          
          if (id.includes('node_modules/highlight.js/')) {
            return 'highlight.js'
          }
          
          if (id.includes('node_modules/katex/')) {
            return 'katex'
          }
          
          // PDF 相关库
          if (id.includes('node_modules/pdfjs-dist/') || 
              id.includes('node_modules/vue-pdf-embed/') ||
              id.includes('node_modules/mammoth/')) {
            return 'pdf'
          }
          
          // 图表库
          if (id.includes('node_modules/echarts/') || 
              id.includes('node_modules/vue-echarts/')) {
            return 'echarts'
          }
          
          // 脑图库
          if (id.includes('node_modules/jsmind/')) {
            return 'jsmind'
          }
          
          // 工具库
          if (id.includes('node_modules/axios/')) {
            return 'utils'
          }
          
          if (id.includes('node_modules/html2canvas/')) {
            return 'canvas'
          }
          
          // 其他 node_modules 包
          if (id.includes('node_modules/')) {
            return 'vendor'
          }
        },
        // 优化 chunk 文件名
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: 'assets/[ext]/[name]-[hash].[ext]'
      }
    },
    // 压缩配置
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true, // 生产环境移除 console
        drop_debugger: true,
        pure_funcs: ['console.log', 'console.info', 'console.debug']
      },
      format: {
        comments: false // 移除注释
      }
    },
    // 启用 CSS 代码分割
    cssCodeSplit: true,
    // 设置 chunk 大小警告限制
    chunkSizeWarningLimit: 2000,
    // 启用源映射
    sourcemap: false
  },
  server: {
    port: 3000,
    hmr: {
      overlay: true // 显示错误覆盖层
    },
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true
      },
      // 脑图服务代理：将前端的 /proxy/html/* 代理到脑图服务的 /html/*
      '/proxy/html': {
        target: 'http://localhost:6066',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy/, ''),
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            // 仅在开发环境记录代理错误
            if (process.env.NODE_ENV === 'development') {
              console.error('脑图服务代理错误:', err)
            }
          })
        }
      }
    }
  },
  // 优化依赖预构建
  optimizeDeps: {
    include: [
      'vue',
      'vue-router',
      'pinia',
      'element-plus',
      '@element-plus/icons-vue',
      'axios',
      'marked',
      'highlight.js',
      'katex',
      'mermaid'
    ],
    // 排除不需要预构建的包
    exclude: []
  },
  // 生产环境优化
  define: {
    __VUE_OPTIONS_API__: JSON.stringify(true),
    __VUE_PROD_DEVTOOLS__: JSON.stringify(false)
  }
})
