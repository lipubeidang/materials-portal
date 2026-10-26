<script setup lang="ts">
import { reactive, ref, onMounted } from "vue";
import { api, send, state, notify } from "../api";
const form = reactive({ oldPassword: "", password: "" }),
  linked = ref(false),
  bindPassword = ref(""),
  busy = ref(false);
onMounted(async () => {
  try {
    linked.value = (await api("/api/account/identity")).linked;
  } catch (e) {
    notify((e as Error).message, true);
  }
});
async function change() {
  busy.value = true;
  try {
    await send("/api/account/password", form);
    form.oldPassword = "";
    form.password = "";
    notify("密码已更新");
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    busy.value = false;
  }
}
async function bind() {
  busy.value = true;
  try {
    const result = await send("/api/account/link-center", {
      password: bindPassword.value,
    });
    location.assign(result.url);
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="container account-page">
    <h1>个人中心</h1>
    <div class="account-grid">
      <section class="panel">
        <h2>账号信息</h2>
        <dl>
          <dt>姓名 / 昵称</dt>
          <dd>{{ state.user.name }}</dd>
          <dt>用户名</dt>
          <dd>{{ state.user.username }}</dd>
          <dt>账号角色</dt>
          <dd>{{ state.user.role === "OPERATOR" ? "操作员" : "普通人员" }}</dd>
          <dt>本次登录方式</dt>
          <dd>
            {{ state.user.source === "center" ? "中心统一认证" : "本地账号" }}
          </dd>
          <dt>中心身份关联</dt>
          <dd>{{ linked ? "已关联" : "尚未关联" }}</dd>
        </dl>
        <RouterLink
          v-if="state.user.role === 'OPERATOR'"
          class="btn primary"
          to="/operator"
          >进入内容管理</RouterLink
        >
      </section>
      <div>
        <form
          v-if="state.user.hasPassword"
          class="panel"
          @submit.prevent="change"
        >
          <h2>修改密码</h2>
          <label
            >原密码<input
              type="password"
              v-model="form.oldPassword"
              autocomplete="current-password"
              required /></label
          ><label
            >新密码<input
              type="password"
              v-model="form.password"
              autocomplete="new-password"
              required
              minlength="10" /></label
          ><button class="btn primary" :disabled="busy">保存新密码</button>
        </form>
        <section v-else class="panel">
          <h2>登录说明</h2>
          <p>此账号由中心认证创建，没有本地密码，请继续通过中心登录。</p>
        </section>
        <form
          v-if="!linked && state.user.hasPassword && state.centerEnabled"
          class="panel"
          @submit.prevent="bind"
        >
          <h2>关联中心身份</h2>
          <p class="muted">
            验证当前密码后，将跳转中心验证身份。不会按同名自动合并账号。
          </p>
          <label
            >当前账号密码<input
              v-model="bindPassword"
              type="password"
              required
              autocomplete="current-password" /></label
          ><button class="btn outline" :disabled="busy">验证并前往中心</button>
        </form>
      </div>
    </div>
  </div>
</template>
