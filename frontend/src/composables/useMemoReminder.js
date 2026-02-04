import { onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMemosDue, markMemoDone } from '@/api/memo'

const DUE_POLL_INTERVAL = 30 * 1000

/**
 * 全局备忘录到期提醒：在布局中调用，只要用户停留在应用内（任意页面）就会轮询到期待办并弹窗。
 */
export function useMemoReminder() {
  let duePollTimer = null

  function requestNotificationPermission() {
    if (!('Notification' in window)) return
    if (Notification.permission === 'default') {
      Notification.requestPermission().catch(() => { })
    }
  }

  async function checkDue() {
    try {
      const res = await getMemosDue()
      // 兼容直接返回数组或 ApiResponse 包装的对象
      const list = Array.isArray(res) ? res : (res?.data || [])

      if (list && list.length > 0) {
        console.log('获取到待提醒备忘录:', list)
        for (const m of list) {
          try {
            if ('Notification' in window && Notification.permission === 'granted') {
              new Notification('备忘录', { body: m.content })
            } else {
              ElMessage.info({
                message: '备忘录：' + m.content,
                duration: 10000, // 增加显示时间到 10s
                showClose: true
              })
            }
            // 提醒后立即标记为已完成，防止下次轮询重复提醒
            await markMemoDone(m.id)
          } catch (e) {
            console.warn('处理待提醒项失败', m.id, e)
          }
        }
      }
    } catch (e) {
      console.error('轮询备忘录失败:', e)
    }
  }

  function startDuePolling() {
    // 立即检查一次
    checkDue()
    // 定时扫描
    duePollTimer = setInterval(checkDue, DUE_POLL_INTERVAL)
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
