<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { send, state, refreshUser, notify } from "../api";
import materialsDataCenterLogo from "../assets/新材料大数据中心Logo.png";
const route = useRoute(),
  router = useRouter();
const isRegister = computed(() => route.path === "/register");
const form = reactive({
  username: "",
  name: "",
  password: "",
  confirmation: "",
});
const busy = ref(false),
  error = ref("");
const handleSubmit = async () => {
  error.value = "";
  if (isRegister.value && form.password !== form.confirmation) {
    error.value = "两次输入的密码不一致";
    return;
  }
  busy.value = true;
  try {
    await send("/api/auth/" + (isRegister.value ? "register" : "login"), form);
    if (isRegister.value) {
      notify("注册成功，请使用新账号登录");
      form.password = "";
      form.confirmation = "";
      router.push("/login");
    } else {
      await refreshUser();
      router.push("/");
    }
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
};
</script>
<template>
  <div class="auth-background">
    <header class="auth-topbar">
      <RouterLink to="/" class="auth-brand" aria-label="返回门户首页">
        <img
          :src="materialsDataCenterLogo"
          alt="新材料大数据中心 National Materials Big Data Center"
          width="300"
          height="60"
        />
        <span class="auth-brand-divider" aria-hidden="true"></span>
        <span class="auth-brand-copy">
          <strong>新一代生物医用材料数据资源节点</strong>
          <small>NEW GENERATION BIOMEDICAL MATERIALS DATA NODE</small>
        </span>
      </RouterLink>
      <RouterLink to="/" class="auth-home-link">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m14 6-6 6 6 6" />
          <path d="M8 12h11" />
        </svg>
        返回首页
      </RouterLink>
    </header>
    <div class="auth-stage">
      <div class="auth-shell">
        <div class="auth-story">
          <span class="eyebrow">MATERIALS CONNECT</span>
          <h1>连接数据<br />发现材料的<br /><em>更多可能</em></h1>
          <p>一个入口，汇聚材料资讯与创新资源。</p>
          <div class="auth-story-grid" aria-hidden="true"></div>
          <div class="auth-orb" aria-hidden="true">◈</div>
        </div>
        <form class="auth-form" @submit.prevent="handleSubmit">
          <span class="mini-label">欢迎来到{{ state.site.name }}</span>
          <h2>{{ isRegister ? "创建账号" : "账号登录" }}</h2>
          <p>
            {{
              isRegister
                ? "注册后可使用本站个人中心"
                : "使用本地账号，或通过中心统一认证进入"
            }}
          </p>
          <div
            v-if="error || route.query.error"
            class="inline-error"
            role="alert"
          >
            {{ error || route.query.error }}
          </div>
          <label
            >用户名<input
              v-model="form.username"
              required
              pattern="[A-Za-z0-9_]{3,32}"
              autocomplete="username"
              placeholder="3—32 位字母、数字或下划线" /></label
          ><label v-if="isRegister"
            >姓名 / 昵称<input
              v-model="form.name"
              required
              maxlength="60"
              autocomplete="nickname"
              placeholder="请输入姓名或昵称" /></label
          ><label
            >密码<input
              v-model="form.password"
              type="password"
              required
              :minlength="isRegister ? 10 : undefined"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
              placeholder="请输入密码" /></label
          ><label v-if="isRegister"
            >确认密码<input
              v-model="form.confirmation"
              type="password"
              required
              autocomplete="new-password"
              placeholder="再次输入密码" /></label
          ><button class="btn primary wide" :disabled="busy">
            {{
              busy ? "处理中…" : isRegister ? "注册普通人员账号" : "登录"
            }}</button
          ><template v-if="!isRegister"
            ><div class="divider">其他登录方式</div>
            <a
              v-if="state.centerEnabled"
              class="btn outline wide"
              href="/auth/center/login"
              >中心统一认证登录 →</a
            >
            <p v-else class="muted small">
              中心统一认证尚未配置，当前可使用本地账号。
            </p></template
          >
          <p class="auth-switch">
            {{ isRegister ? "已有账号？" : "还没有账号？" }}
            <RouterLink :to="isRegister ? '/login' : '/register'">{{
              isRegister ? "立即登录" : "注册账号"
            }}</RouterLink>
          </p>
        </form>
      </div>
    </div>
    <footer class="auth-footer">
      <span>版权所有：{{ state.site.name }}</span>
      <span>新一代生物医用材料数据资源节点</span>
    </footer>
  </div>
</template>
