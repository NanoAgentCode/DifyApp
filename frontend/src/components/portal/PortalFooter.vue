<template>
  <div class="portal-footer">
    <div class="footer-content">
      <el-icon class="footer-icon"><InfoFilled /></el-icon>
      <span class="footer-text">内容由AI生成，仅供参考</span>
    </div>
  </div>
</template>

<script setup>
import { InfoFilled } from '@element-plus/icons-vue'
</script>

<style scoped>
.portal-footer { text-align: center; padding: 10px 20px; background: linear-gradient(180deg, rgba(255, 255, 255, .95) 0%, rgba(255, 255, 255, .98) 100%); border-top: 1px solid rgba(0, 0, 0, .04); transition: all .3s ease; }
.portal-footer:hover { box-shadow: 0 -4px 16px rgba(0, 0, 0, .06); }
.footer-content { display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 6px 14px; border-radius: 20px; background: rgba(64, 158, 255, .06); transition: all .3s ease; }
.portal-footer:hover .footer-content { background: rgba(64, 158, 255, .1); transform: translateY(-1px); }
.footer-icon { font-size: 12px; color: var(--el-color-primary, #409eff); animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: .7; transform: scale(1.05); } }
.footer-text { font-size: 12px; color: var(--el-text-color-regular, #606266); font-weight: 400; letter-spacing: .3px; line-height: 1.4; }
@media (max-width: 768px) { .portal-footer { padding: 8px 16px; } }
</style>
