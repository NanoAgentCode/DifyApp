import { onMounted, onUnmounted } from 'vue'
import { ElNotification } from 'element-plus'
import { getMemosDue, markMemoDone } from '@/api/memo'

const DUE_POLL_INTERVAL = 30 * 1000

/**
 * 全局备忘录到期提醒
 */
export function useMemoReminder() {
  let duePollTimer = null

  function requestNotificationPermission() {
    if (!('Notification' in window)) return
    if (Notification.permission === 'default') {
      Notification.requestPermission().catch(() => { })
    }
  }

  // 简单的提示音
  function playBeep() {
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext
      if (!AudioContext) return
      const ctx = new AudioContext()
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.setValueAtTime(880, ctx.currentTime) // A5
      gain.gain.setValueAtTime(0.1, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5)
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start()
      osc.stop(ctx.currentTime + 0.5)
    } catch (e) {
      console.warn('播放提示音失败', e)
    }
  }

  async function checkDue() {
    try {
      console.log('正在检查到期备忘录...', new Date().toLocaleTimeString())
      const res = await getMemosDue()
      const list = Array.isArray(res) ? res : (res?.data || [])

      if (list && list.length > 0) {
        console.log('%c[MEMO] 获取到待提醒备忘录:', 'color: #e6a23c; font-weight: bold;', list)
        playBeep()

        for (const m of list) {
          try {
            if ('Notification' in window && Notification.permission === 'granted') {
              new Notification('备忘录提醒', {
                body: m.content,
                icon: '/favicon.ico'
              })
            }

            // 无论系统通知是否开启，都在应用内弹出 Notification（更醒目）
            ElNotification({
              title: '备忘录提醒',
              message: m.content,
              type: 'warning',
              duration: 0, // 不自动关闭，直到手动点掉或确认
              position: 'bottom-right',
              offset: 20
            })

            // 提醒后标记为已完成
            await markMemoDone(m.id)
            console.log(`[MEMO] 已成功提醒并标记 id=${m.id}`)
          } catch (e) {
            console.warn('处理待提醒项失败', m.id, e)
          }
        }
      }
    } catch (e) {
      console.debug('轮询备忘录静默跳过:', e.message)
    }
  }

  function startDuePolling() {
    checkDue()
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
