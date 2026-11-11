<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { state, logout } from "./api";
import PortalHeader from "./components/PortalHeader.vue";
import PortalFooter from "./components/PortalFooter.vue";
const route = useRoute();
const admin = computed(() => route.path.startsWith("/operator"));
const auth = computed(() => ["/login", "/register"].includes(route.path));
const home = computed(() => route.path === "/");
</script>
<template>
  <div v-if="!state.ready" class="loading">正在载入新材料门户…</div>
  <div
    v-else
    :class="{
      'portal-shell': !admin && !auth,
      'homepage-shell': home,
      'auth-layout': auth,
    }"
  >
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
    <PortalHeader v-else-if="!auth" :home="home" />
    <main :class="{ 'admin-main': admin, 'auth-main': auth }">
      <RouterView />
    </main>
    <PortalFooter v-if="!admin && !auth" />
  </div>
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
