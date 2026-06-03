import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { fileURLToPath } from 'url'
import { nodePolyfills } from 'vite-plugin-node-polyfills'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function manualChunks(id) {
  if (!id.includes('node_modules')) {
    return undefined
  }

  const hasPkg = (name) => id.includes(`/node_modules/${name}/`)

  if (hasPkg('vue') || hasPkg('vue-router') || hasPkg('pinia')) {
    return 'vendor-vue'
  }

  if (hasPkg('element-plus') || hasPkg('@element-plus/icons-vue')) {
    return 'vendor-element-plus'
  }

  if (hasPkg('marked') || hasPkg('marked-highlight')) {
    return 'vendor-marked'
  }

  if (hasPkg('highlight.js')) {
    const languageMatch = id.match(/highlight\.js[\\/](?:lib[\\/])?languages[\\/]([^\\/]+)/)
    if (languageMatch) {
      const first = languageMatch[1].charAt(0).toLowerCase()
      if (first >= 'a' && first <= 'f') return 'vendor-highlight-lang-a-f'
      if (first >= 'g' && first <= 'm') return 'vendor-highlight-lang-g-m'
      if (first >= 'n' && first <= 's') return 'vendor-highlight-lang-n-s'
      return 'vendor-highlight-lang-t-z'
    }
    return 'vendor-highlight'
  }

  if (hasPkg('katex')) {
    return 'vendor-katex'
  }

  if (id.includes('pdf.worker')) {
    return 'vendor-pdf-worker'
  }

  if (id.includes('pdf.sandbox')) {
    return 'vendor-pdf-sandbox'
  }

  if (hasPkg('pdfjs-dist') || hasPkg('vue-pdf-embed')) {
    return 'vendor-pdf'
  }

  if (hasPkg('mammoth') || hasPkg('jszip')) {
    return 'vendor-docx'
  }

  if (hasPkg('@antv/infographic')) {
    return 'vendor-antv'
  }

  if (hasPkg('d3') || id.includes('/node_modules/d3-')) {
    return 'vendor-d3'
  }

  if (hasPkg('echarts') || hasPkg('vue-echarts') || hasPkg('zrender')) {
    if (hasPkg('zrender')) {
      return 'vendor-zrender'
    }
    return 'vendor-echarts'
  }

  if (hasPkg('jsmind') || hasPkg('html2canvas')) {
    return 'vendor-visual-tools'
  }

  if (
    id.includes('browserify-') ||
    id.includes('node-stdlib-browser') ||
    hasPkg('buffer') ||
    hasPkg('process') ||
    hasPkg('stream-browserify') ||
    hasPkg('readable-stream') ||
    hasPkg('crypto-browserify') ||
    hasPkg('path-browserify') ||
    hasPkg('url') ||
    hasPkg('util')
  ) {
    return 'vendor-node-polyfills'
  }

  if (hasPkg('axios')) {
    return 'vendor-http'
  }

  if (hasPkg('lodash') || hasPkg('lodash-es') || hasPkg('lodash-unified')) {
    return 'vendor-lodash'
  }

  if (
    hasPkg('@vueuse/core') ||
    hasPkg('@vueuse/shared') ||
    hasPkg('@floating-ui/dom') ||
    hasPkg('@popperjs/core') ||
    hasPkg('async-validator') ||
    hasPkg('dayjs') ||
    hasPkg('normalize-wheel-es')
  ) {
    return 'vendor-ui-support'
  }

  if (
    hasPkg('postcss') ||
    hasPkg('source-map') ||
    hasPkg('source-map-js') ||
    hasPkg('htmlparser2') ||
    hasPkg('linkedom') ||
    hasPkg('@xmldom/xmldom') ||
    hasPkg('entities') ||
    hasPkg('domhandler') ||
    hasPkg('domutils') ||
    hasPkg('dom-serializer')
  ) {
    return 'vendor-parsing'
  }

  return 'vendor'
}

// 精简版 Vite 配置：保留必要功能，按重型依赖域做稳定分包
export default defineConfig({
  plugins: [
    vue(),
    // Node.js 模块 polyfill（主要是为 @antv/infographic 等依赖）
    nodePolyfills({
      include: ['path', 'fs', 'url', 'buffer', 'process', 'util', 'stream', 'events'],
      globals: {
        Buffer: true,
        global: true,
        process: true,
      },
      protocolImports: true,
    })
  ],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      // 为 source-map-js 提供浏览器兼容的空模块，解决 postcss 等依赖问题
      'source-map-js': path.resolve(__dirname, 'src/utils/empty-module.js'),
    },
    conditions: ['browser', 'module', 'import', 'default'],
  },

  build: {
    target: 'es2015',
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks,
      },
    },
  },

  server: {
    port: 3000,
    hmr: {
      overlay: true,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
      },
      // 脑图服务代理：将前端的 /proxy/html/* 代理到脑图服务的 /html/*
      '/proxy/html': {
        target: 'http://localhost:16066',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/proxy/, ''),
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            if (process.env.NODE_ENV === 'development') {
              // 仅在开发环境打印代理错误
              console.error('脑图服务代理错误:', err)
            }
          })
        }
      }
    }
  },

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
      'postcss',
      'source-map-js',
      '@antv/infographic',
    ],
    esbuildOptions: {
      define: {
        global: 'globalThis',
      },
      platform: 'browser',
      mainFields: ['browser', 'module', 'main'],
      format: 'esm',
    },
  },

  define: {
    __VUE_OPTIONS_API__: JSON.stringify(true),
    __VUE_PROD_DEVTOOLS__: JSON.stringify(false),
    global: 'globalThis',
  },
})
