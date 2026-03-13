import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { fileURLToPath } from 'url'
import { nodePolyfills } from 'vite-plugin-node-polyfills'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// 精简版 Vite 配置：保留必要功能，去掉过度手工分包和复杂 Terser 配置
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
