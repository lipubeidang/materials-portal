<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import { useRoute } from "vue-router";
import { api, send, upload, notify, refreshSite, date, type Row } from "../api";
import ConstructionPartners from "../components/ConstructionPartners.vue";
const route = useRoute(),
  categories = ref<Row[]>([]),
  site = ref<Row>({}),
  logs = ref<Row[]>([]),
  busy = ref(false),
  editing = ref(false),
  uploading = ref<Row | null>(null),
  category = reactive<Row>({});
const groups = [
  {
    key: "banners",
    label: "首页轮播",
    image: true,
    hint: "每张轮播分别设置标签、标题、说明、按钮和图片，切换时图文同步变化。建议上传 1920×600 横幅（PNG/JPG，10 MB 以内）；未上传时显示默认插画。",
  },
  {
    key: "links",
    label: "快捷入口",
    image: true,
    hint: "首页搜索区域右侧展示前两个入口。",
  },
  {
    key: "partners",
    label: "建设单位",
    image: true,
    hint: "默认展示参考图中的七家单位。添加自定义单位后，将按下方列表展示：标识保持原比例，全部展示、自动换行居中。支持 PNG/JPG，单张不超过 10 MB；保存后生效。",
  },
  {
    key: "navigation",
    label: "额外导航 / 外部业务入口",
    image: false,
    hint: "填写已有业务的名称和跳转地址。",
  },
];
async function load() {
  try {
    if (route.params.tab === "categories")
      categories.value = await api("/api/operator/categories");
    else if (route.params.tab === "site") {
      site.value = await api("/api/operator/site");
      for (const group of groups) site.value[group.key] ||= [];
      for (const banner of site.value.banners) {
        banner.eyebrow ??= "让数据，转化为材料创新能力";
        banner.buttonText ||= "探索更多";
      }
    } else logs.value = await api("/api/operator/audit");
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
  if (uploading.value) return;
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
  const input = e.target as HTMLInputElement;
  const f = input.files?.[0];
  if (!f) return;
  input.value = "";
  if (
    !/\.(png|jpe?g)$/i.test(f.name) ||
    !["image/png", "image/jpeg"].includes(f.type)
  ) {
    notify("请上传 PNG 或 JPG 图片", true);
    return;
  }
  if (f.size > 10 * 1024 * 1024) {
    notify("图片不能超过 10 MB", true);
    return;
  }
  uploading.value = item;
  try {
    item.image = (await upload(f)).url;
    notify("图片已上传，请保存站点配置使其在首页生效");
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    uploading.value = null;
  }
}
function addItem(key: string) {
  site.value[key] ||= [];
  site.value[key].push({
    title: "",
    description: "",
    url: "",
    image: "",
    ...(key === "banners"
      ? { eyebrow: "让数据，转化为材料创新能力", buttonText: "探索更多" }
      : {}),
  });
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
      ><label>英文名称<input v-model="site.englishName" /></label>
      <label
        >备案号<input
          v-model="site.registrationNumber"
          maxlength="160"
          placeholder="填写实际备案号"
      /></label>
      <label
        >备案查询链接<input
          v-model="site.registrationUrl"
          maxlength="500"
          placeholder="https://…（选填）"
      /></label>
    </div>
    <p class="settings-hint">
      门户底部统一显示累计访问量、备案号和版权信息；备案号留空时不显示。
    </p>
    <section
      class="settings-group"
      :class="{ 'partner-settings': group.key === 'partners' }"
      v-for="group in groups"
      :key="group.key"
      :aria-label="group.label"
    >
      <h3>
        {{ group.label
        }}<button
          type="button"
          class="btn"
          :disabled="busy || !!uploading"
          @click="addItem(group.key)"
        >
          ＋ 添加
        </button>
      </h3>
      <p class="settings-hint">{{ group.hint }}</p>
      <div
        v-if="group.key === 'partners' && !site.partners?.length"
        class="settings-partner-preview"
      >
        <ConstructionPartners :partners="[]" />
      </div>
      <p
        class="muted"
        v-if="!site[group.key]?.length && group.key !== 'partners'"
      >
        尚未配置，点击“添加”后填写并保存即可展示。
      </p>
      <div
        class="setting-row"
        :class="{ 'banner-setting-row': group.key === 'banners' }"
        v-for="(item, i) in site[group.key]"
        :key="i"
      >
        <div class="setting-order">
          <b>{{ Number(i) + 1 }}</b
          ><button
            type="button"
            v-if="Number(i) > 0"
            :disabled="busy || !!uploading"
            :aria-label="`上移${item.title || group.label}`"
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
            >{{ group.key === "partners" ? "单位名称" : "标题"
            }}<input v-model="item.title" required maxlength="100" /></label
          ><label
            >{{ group.key === "partners" ? "官方网站（选填）" : "跳转地址"
            }}<input
              v-model="item.url"
              placeholder="/columns/resources 或 https://…" /></label
          ><label v-if="group.key !== 'partners'" class="full"
            >描述<input v-model="item.description" maxlength="300"
          /></label>
          <template v-if="group.key === 'banners'">
            <label
              >顶部标签（选填）<input
                v-model="item.eyebrow"
                maxlength="100"
                placeholder="留空则不显示标签"
            /></label>
            <label
              >按钮文字<input
                v-model="item.buttonText"
                maxlength="40"
                placeholder="探索更多"
            /></label>
          </template>
        </div>
        <div
          v-if="group.image"
          class="setting-image"
          :aria-busy="uploading === item"
        >
          <img
            v-if="item.image"
            :src="item.image"
            :alt="item.title || '单位图片预览'"
          />
          <span
            v-else-if="group.key === 'partners'"
            class="partner-image-placeholder"
            >单位标识预览</span
          >
          <span
            v-else-if="group.key === 'banners'"
            class="banner-image-placeholder"
            >第 {{ Number(i) + 1 }} 张轮播 · 默认插画</span
          >
          <label class="btn"
            >{{
              uploading === item
                ? "上传中…"
                : item.image
                  ? "更换图片"
                  : "上传图片"
            }}<input
              type="file"
              accept="image/png,image/jpeg"
              :disabled="busy || !!uploading"
              :aria-label="`上传${item.title || group.label}图片`"
              hidden
              @change="image($event, item)" /></label
          ><button
            type="button"
            v-if="item.image"
            :disabled="busy || !!uploading"
            class="text-button"
            @click="item.image = ''"
          >
            移除图片
          </button>
        </div>
        <button
          type="button"
          class="text-button danger-text"
          :disabled="busy || !!uploading"
          @click="site[group.key].splice(Number(i), 1)"
        >
          删除
        </button>
      </div>
    </section>
    <div class="editor-actions">
      <span>保存后立即更新门户展示</span
      ><button class="btn primary" :disabled="busy || !!uploading">
        保存站点配置
      </button>
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
<style scoped>
.settings-partner-preview {
  border: 1px solid #e1e7ef;
  border-radius: 8px;
  background: #fff;
  padding: 20px;
}
.banner-setting-row .setting-image {
  width: 250px;
  flex-shrink: 0;
}
.banner-setting-row .setting-image img,
.banner-image-placeholder {
  width: 250px;
  height: 100px;
  object-fit: contain;
  border-radius: 6px;
  background: #edf4fc;
}
.banner-image-placeholder {
  display: grid;
  place-items: center;
  color: #5f7a98;
  font-size: 13px;
}
@media (max-width: 1000px) {
  .banner-setting-row {
    flex-wrap: wrap;
  }
  .banner-setting-row .setting-fields {
    min-width: 60%;
  }
}
@media (max-width: 700px) {
  .banner-setting-row .setting-fields {
    width: 100%;
  }
}
</style>
