<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import { useRoute } from "vue-router";
import { api, send, upload, notify, refreshSite, date, type Row } from "../api";
const route = useRoute(),
  categories = ref<Row[]>([]),
  site = ref<Row>({}),
  logs = ref<Row[]>([]),
  busy = ref(false),
  editing = ref(false),
  category = reactive<Row>({});
const groups = [
  { key: "banners", label: "首页轮播" },
  { key: "links", label: "快捷入口" },
  { key: "partners", label: "建设单位" },
  { key: "navigation", label: "额外导航 / 外部业务入口" },
];
async function load() {
  try {
    if (route.params.tab === "categories")
      categories.value = await api("/api/operator/categories");
    else if (route.params.tab === "site")
      site.value = await api("/api/operator/site");
    else logs.value = await api("/api/operator/audit");
  } catch (e) {
    notify((e as Error).message, true);
  }
}
watch(() => route.params.tab, load, { immediate: true });
function edit(row?: Row) {
  Object.keys(category).forEach((k) => delete category[k]);
  Object.assign(
    category,
    row || {
      name: "",
      slug: "",
      parent_id: "",
      type: "ARTICLE",
      template: "list",
      sort_order: 0,
      enabled: true,
      navigation: true,
    },
  );
  editing.value = true;
}
async function saveCategory() {
  busy.value = true;
  try {
    await send("/api/operator/categories", category);
    editing.value = false;
    await load();
    await refreshSite();
    notify("栏目已保存");
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    busy.value = false;
  }
}
async function remove(row: Row) {
  if (!confirm("确认删除此栏目？含内容或子栏目的栏目无法删除。")) return;
  try {
    await api("/api/operator/categories/" + row.id, { method: "DELETE" });
    await load();
    await refreshSite();
  } catch (e) {
    notify((e as Error).message, true);
  }
}
async function saveSite() {
  busy.value = true;
  try {
    site.value = await send("/api/operator/site", site.value, "PUT");
    await refreshSite();
    notify("站点配置已更新");
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    busy.value = false;
  }
}
async function image(e: Event, item: Row) {
  const f = (e.target as HTMLInputElement).files?.[0];
  if (!f) return;
  busy.value = true;
  try {
    item.image = (await upload(f)).url;
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    busy.value = false;
  }
}
const actions: Row = {
  SAVE_CONTENT: "保存草稿",
  PUBLISH: "发布内容",
  WITHDRAW: "撤回内容",
  DELETE: "删除内容",
  RESTORE: "恢复内容",
  SAVE_CATEGORY: "保存栏目",
  DELETE_CATEGORY: "删除栏目",
  SAVE_SITE: "更新站点",
  REGISTER: "用户注册",
  LOGIN_LOCAL: "本地登录",
  LOGIN_CENTER: "中心登录",
  CHANGE_PASSWORD: "修改密码",
  BIND_CENTER: "绑定中心身份",
  CENTER_LOGOUT: "中心退出",
  BOOTSTRAP_OPERATOR: "初始化操作员",
};
</script>
<template>
  <div class="admin-breadcrumb">
    <RouterLink to="/operator">内容管理</RouterLink> ›
    {{
      route.params.tab === "categories"
        ? "栏目设置"
        : route.params.tab === "site"
          ? "站点配置"
          : "操作记录"
    }}
  </div>
  <section
    class="admin-panel settings-panel"
    v-if="route.params.tab === 'categories'"
  >
    <h2>
      栏目设置 <button class="btn primary" @click="edit()">＋ 新增栏目</button>
    </h2>
    <p class="settings-intro">
      栏目决定内容归属，类型决定编辑字段，模板决定前台展示方式。
    </p>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>栏目名称</th>
            <th>路径标识</th>
            <th>上级栏目</th>
            <th>内容类型</th>
            <th>展示模板</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in categories" :key="c.id">
            <td>{{ c.parent_id ? "└ " : "" }}{{ c.name }}</td>
            <td>{{ c.slug }}</td>
            <td>
              {{
                categories.find((x) => x.id === c.parent_id)?.name || "顶级栏目"
              }}
            </td>
            <td>{{ c.type }}</td>
            <td>{{ c.template }}</td>
            <td>{{ c.enabled ? "启用" : "停用" }}</td>
            <td class="row-actions">
              <button @click="edit(c)">编辑</button
              ><button class="danger-text" @click="remove(c)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <form
    v-else-if="route.params.tab === 'site'"
    class="admin-panel settings-panel"
    @submit.prevent="saveSite"
  >
    <h2>站点配置</h2>
    <div class="form-grid settings-basic">
      <label>站点名称<input v-model="site.name" required /></label
      ><label>英文名称<input v-model="site.englishName" /></label
      ><label class="full">页脚说明<input v-model="site.footer" /></label
      ><label class="full">联系方式<input v-model="site.contact" /></label>
    </div>
    <section class="settings-group" v-for="group in groups" :key="group.key">
      <h3>
        {{ group.label
        }}<button
          type="button"
          class="btn"
          @click="
            site[group.key].push({
              title: '',
              description: '',
              url: '',
              image: '',
            })
          "
        >
          ＋ 添加
        </button>
      </h3>
      <p class="muted" v-if="!site[group.key]?.length">
        尚未配置，门户将隐藏此区域的空入口。
      </p>
      <div class="setting-row" v-for="(item, i) in site[group.key]" :key="i">
        <div class="setting-order">
          <b>{{ Number(i) + 1 }}</b
          ><button
            type="button"
            v-if="Number(i) > 0"
            @click="
              [site[group.key][Number(i) - 1], site[group.key][i]] = [
                site[group.key][i],
                site[group.key][Number(i) - 1],
              ]
            "
          >
            ↑
          </button>
        </div>
        <div class="setting-fields">
          <label
            >标题<input v-model="item.title" required maxlength="100" /></label
          ><label
            >跳转地址<input
              v-model="item.url"
              placeholder="/columns/resources 或 https://…" /></label
          ><label class="full"
            >描述<input v-model="item.description" maxlength="300"
          /></label>
        </div>
        <div class="setting-image">
          <img v-if="item.image" :src="item.image" alt="配置图片" /><label
            class="btn"
            >{{ item.image ? "更换图片" : "上传图片"
            }}<input
              type="file"
              accept="image/png,image/jpeg"
              hidden
              @change="image($event, item)" /></label
          ><button
            type="button"
            v-if="item.image"
            class="text-button"
            @click="item.image = ''"
          >
            移除图片
          </button>
        </div>
        <button
          type="button"
          class="text-button danger-text"
          @click="site[group.key].splice(Number(i), 1)"
        >
          删除
        </button>
      </div>
    </section>
    <div class="editor-actions">
      <span>保存后立即更新门户展示</span
      ><button class="btn primary" :disabled="busy">保存站点配置</button>
    </div>
  </form>
  <section v-else class="admin-panel settings-panel">
    <h2>最近操作记录</h2>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>动作</th>
            <th>对象编号</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td>{{ date(log.created_at) }}</td>
            <td>{{ log.display_name || "系统" }}</td>
            <td>{{ actions[log.action] || log.action }}</td>
            <td class="mono">{{ log.object_id }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <div v-if="editing" class="modal-backdrop" @click.self="editing = false">
    <form class="modal" @submit.prevent="saveCategory">
      <div class="modal-head">
        <strong>{{ category.id ? "编辑" : "新增" }}栏目</strong
        ><button type="button" @click="editing = false">×</button>
      </div>
      <div class="form-grid">
        <label
          >栏目名称<input
            v-model="category.name"
            required
            maxlength="80" /></label
        ><label
          >路径标识<input
            v-model="category.slug"
            required
            pattern="[a-z][a-z0-9-]{0,79}"
            placeholder="如：policies" /></label
        ><label
          >上级栏目<select v-model="category.parent_id">
            <option value="">顶级栏目</option>
            <option
              v-for="c in categories.filter(
                (c) => c.id !== category.id && !c.parent_id,
              )"
              :key="c.id"
              :value="c.id"
            >
              {{ c.name }}
            </option>
          </select></label
        ><label
          >内容类型<select v-model="category.type">
            <option value="ARTICLE">图文文章</option>
            <option value="PAGE">单页介绍</option>
            <option value="RESOURCE">数据资源</option>
            <option value="PRODUCT">数据产品</option>
            <option value="LINK">外部链接</option>
          </select></label
        ><label
          >展示模板<select v-model="category.template">
            <option value="list">图文列表</option>
            <option value="date">日期列表</option>
            <option value="cards">卡片列表</option>
            <option value="page">单页</option>
          </select></label
        ><label
          >排序（小值优先）<input
            type="number"
            v-model.number="category.sort_order" /></label
        ><label class="check-label"
          ><input type="checkbox" v-model="category.enabled" />启用栏目</label
        ><label class="check-label"
          ><input
            type="checkbox"
            v-model="category.navigation"
          />显示在导航</label
        >
      </div>
      <button class="btn primary" :disabled="busy">保存栏目</button>
    </form>
  </div>
</template>
