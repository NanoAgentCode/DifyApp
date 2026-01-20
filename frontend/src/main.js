import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'highlight.js/styles/vs2015.css' // highlight.js 官方 VS Code 2015 Dark 主题
import '@/styles/vscode-dark.css' // 自定义 VS Code Dark+ 主题（作为补充，优先级更高）
import '@/styles/design-tokens.css' // 企业级设计令牌
import '@/styles/enterprise-base.css' // 企业级基础样式
import '@/styles/animations.css' // 企业级动画和微交互
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import router from './router'

const app = createApp(App)
const pinia = createPinia()

// 配置 Vue 警告过滤器（抑制 Element Plus 内部组件的已知警告）
// 这是 Element Plus ElMenuCollapseTransition 组件的已知问题，不影响功能
if (process.env.NODE_ENV === 'development') {
  const originalWarn = console.warn
  console.warn = (...args) => {
    // 过滤掉 Element Plus ElMenuCollapseTransition 的已知警告
    const message = args[0]
    if (message && typeof message === 'string' && 
        message.includes('Slot "default" invoked outside of the render function') &&
        (message.includes('ElMenuCollapseTransition') || message.includes('BaseTransition'))) {
      // 静默忽略这个警告（Element Plus 内部组件的问题，不影响功能）
      return
    }
    // 其他警告正常输出
    originalWarn.apply(console, args)
  }
}

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')

