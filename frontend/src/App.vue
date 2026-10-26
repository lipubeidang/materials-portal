<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { state, logout } from "./api";
import PortalHeader from "./components/PortalHeader.vue";
const route = useRoute();
const admin = computed(() => route.path.startsWith("/operator"));
</script>
<template>
  <div v-if="!state.ready" class="loading">正在载入新材料门户…</div>
  <template v-else>
    <header v-if="admin" class="admin-top">
      <RouterLink to="/" class="admin-brand"
        ><span class="brand-mark">M<span>·</span>D</span
        ><strong
          >{{ state.site.name }}<small>内容发布系统</small></strong
        ></RouterLink
      >
      <nav>
        <RouterLink to="/operator">内容管理</RouterLink
        ><RouterLink to="/operator/settings/categories">栏目设置</RouterLink
        ><RouterLink to="/operator/settings/site">站点配置</RouterLink
        ><RouterLink to="/operator/settings/audit">操作记录</RouterLink>
      </nav>
      <div class="admin-user">
        {{ state.user.name }} · 操作员 <button @click="logout">退出</button>
      </div>
    </header>
    <PortalHeader v-else />
    <main :class="{ 'admin-main': admin }"><RouterView /></main>
    <footer v-if="!admin" class="footer">
      <div class="container">
        <strong>{{ state.site.name }}</strong
        ><span>{{ state.site.footer }}</span
        ><span v-if="state.site.contact">{{ state.site.contact }}</span>
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
          累计访问量
          <b>{{
            state.totalVisits === null
              ? "—"
              : state.totalVisits.toLocaleString("zh-CN")
          }}</b>
          次
        </span>
        <small>NEW MATERIALS · NEW POSSIBILITIES</small>
      </div>
    </footer>
  </template>
  <div
    v-if="state.notice || state.error"
    class="toast"
    :class="{ error: state.error }"
    role="alert"
  >
    {{ state.error || state.notice
    }}<button
      @click="
        state.error = '';
        state.notice = '';
      "
      aria-label="关闭提示"
    >
      ×
    </button>
  </div>
</template>
