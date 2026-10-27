<script setup lang="ts">
import { state } from "../api";
import footerLogo from "../assets/footer-logo.png";
</script>
<template>
  <footer class="portal-footer">
    <div class="container portal-footer-inner">
      <RouterLink to="/" class="portal-footer-brand">
        <img
          :src="footerLogo"
          width="342"
          height="97"
          alt="新材料大数据中心 National Materials Big Data Center"
        />
      </RouterLink>
      <div class="portal-footer-info">
        <span
          class="visit-count"
          role="status"
          title="每次打开或切换前台页面计 1 次访问"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"
              stroke="currentColor"
              stroke-width="1.5"
            />
            <circle
              cx="12"
              cy="12"
              r="3"
              stroke="currentColor"
              stroke-width="1.5"
            />
          </svg>
          累计访问量：<b>{{
            state.totalVisits === null
              ? "—"
              : state.totalVisits.toLocaleString("zh-CN")
          }}</b>
        </span>
        <div class="portal-footer-legal">
          <component
            v-if="state.site.registrationNumber"
            :is="state.site.registrationUrl ? 'a' : 'span'"
            :href="state.site.registrationUrl || undefined"
            :target="state.site.registrationUrl ? '_blank' : undefined"
            :rel="
              state.site.registrationUrl ? 'noopener noreferrer' : undefined
            "
            >{{ state.site.registrationNumber }}</component
          >
          <span>版权所有：新材料大数据中心</span>
        </div>
      </div>
    </div>
  </footer>
</template>
