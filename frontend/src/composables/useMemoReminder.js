import { onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMemosDue, markMemoDone } from '@/api/memo'

const DUE_POLL_INTERVAL = 60 * 1000

/**
 * 全局备忘录到期提醒：在布局中调用，只要用户停留在应用内（任意页面）就会轮询到期待办并弹窗。
 * 解决「只有打开备忘录列表页才轮询」导致收不到提醒的问题。
 */
export function useMemoReminder() {
  let duePollTimer = null

  function requestNotificationPermission() {
    if (!('Notification' in window)) return
    if (Notification.permission === 'default') {
      Notification.requestPermission().catch(() => {})
    }
  }

  function startDuePolling() {
    duePollTimer = setInterval(async () => {
      try {
        const list = await getMemosDue()
        if (list && list.length > 0) {
          for (const m of list) {
            try {
              if ('Notification' in window && Notification.permission === 'granted') {
                new Notification('备忘录', { body: m.content })
              } else {
                ElMessage.info({ message: '备忘录：' + m.content, duration: 5000 })
              }
              await markMemoDone(m.id)
            } catch (e) {
              console.warn('标记已提醒失败', m.id, e)
            }
          }
        }
      } catch (e) {
        // 静默失败（如未登录、网络错误）
      }
    }, DUE_POLL_INTERVAL)
  }

  onMounted(() => {
    requestNotificationPermission()
    startDuePolling()
  })

  onUnmounted(() => {
    if (duePollTimer) {
      clearInterval(duePollTimer)
      duePollTimer = null
    }
  })
}
