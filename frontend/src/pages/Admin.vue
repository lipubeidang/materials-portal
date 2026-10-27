<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { api, send, notify, date, type Row } from "../api";
import PublishStatusSelect from "../components/PublishStatusSelect.vue";
import {
  industryRegions,
  industryTopics,
  topicsFor,
  industryLabel,
  isIndustryCategory,
} from "../industry";
const router = useRouter(),
  categories = ref<Row[]>([]),
  category = ref(""),
  q = ref(""),
  status = ref(""),
  page = ref(1),
  items = ref<Row[]>([]),
  total = ref(0),
  selected = ref<string[]>([]),
  busy = ref(false),
  error = ref("");
const industryRegion = ref(""),
  industryTopic = ref("");
const industrySelected = computed(() =>
  isIndustryCategory(category.value, categories.value),
);
const currentColumn = computed(() =>
  [
    categories.value.find((c) => c.id === category.value)?.name || "全部",
    industrySelected.value &&
      industryRegions.find((r) => r.value === industryRegion.value)?.label,
    industrySelected.value &&
      industryTopics.find((t) => t.value === industryTopic.value)?.label,
  ]
    .filter(Boolean)
    .join(" / "),
);
let requestId = 0;
async function load() {
  const request = ++requestId;
  busy.value = true;
  error.value = "";
  try {
    const data = await api(
      "/api/operator/contents?" +
        new URLSearchParams({
          category: category.value,
          q: q.value,
          status: status.value,
          industryRegion: industryRegion.value,
          industryTopic: industryTopic.value,
          page: String(page.value),
        }),
    );
    if (request !== requestId) return;
    items.value = data.items;
    total.value = data.total;
    selected.value = [];
  } catch (e) {
    if (request === requestId) error.value = (e as Error).message;
  } finally {
    if (request === requestId) busy.value = false;
  }
}
onMounted(async () => {
  try {
    categories.value = await api("/api/operator/categories");
    await load();
  } catch (e) {
    notify((e as Error).message, true);
  }
});
function filter(id: string, region = "", topic = "") {
  category.value = id;
  industryRegion.value = region;
  industryTopic.value = topic;
  page.value = 1;
  load();
}
function add() {
  router.push({
    path: "/operator/edit/new",
    query: {
      category: category.value || "headlines",
      region: industryRegion.value || undefined,
      topic: industryTopic.value || undefined,
    },
  });
}
async function action(item: Row, name: string) {
  if (
    !confirm(
      `确认${({ publish: "发布", withdraw: "撤回", delete: "移入回收站", restore: "恢复为草稿" } as Row)[name]}“${item.title}”？`,
    )
  )
    return;
  try {
    await send(`/api/operator/contents/${item.id}/${name}`, {
      version: item.version,
    });
    notify("操作成功");
    await load();
  } catch (e) {
    notify((e as Error).message, true);
  }
}
async function batch(action: string) {
  if (!selected.value.length) {
    notify("请先勾选内容", true);
    return;
  }
  if (
    !confirm(
      `确认对选中的 ${selected.value.length} 条内容执行${action === "publish" ? "发布" : "撤回"}？`,
    )
  )
    return;
  busy.value = true;
  try {
    const result = await send("/api/operator/batch", {
      action,
      items: items.value
        .filter((i) => selected.value.includes(i.id))
        .map((i) => ({ id: i.id, version: i.version })),
    });
    const failed = result.filter((r: Row) => !r.ok);
    await load();
    notify(
      failed.length
        ? `${failed.length} 条失败：${failed.map((r: Row) => r.message).join("；")}`
        : "批量操作成功",
      failed.length > 0,
    );
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="admin-breadcrumb">
    当前位置： <RouterLink to="/">首页</RouterLink> › 内容管理
  </div>
  <div class="admin-workspace">
    <aside class="tree-panel">
      <h3>⌂ 门户内容栏目</h3>
      <button :class="{ active: !category }" @click="filter('')">
        ▤ 全部内容</button
      ><template
        v-for="root in categories.filter((c) => !c.parent_id)"
        :key="root.id"
        ><button
          :class="{ active: category === root.id && !industryRegion }"
          @click="filter(root.id)"
        >
          ▸ ▰ {{ root.name }} <small v-if="!root.enabled">停用</small></button
        ><button
          class="tree-child"
          v-for="child in categories.filter((c) => c.parent_id === root.id)"
          :key="child.id"
          :class="{ active: category === child.id }"
          @click="filter(child.id)"
        >
          └ {{ child.name }}
        </button>
        <template v-if="root.id === 'industry'">
          <template v-for="region in industryRegions" :key="region.value">
            <button
              class="tree-child"
              :class="{
                active:
                  category === root.id &&
                  industryRegion === region.value &&
                  !industryTopic,
              }"
              :aria-pressed="
                category === root.id &&
                industryRegion === region.value &&
                !industryTopic
              "
              @click="filter(root.id, region.value)"
            >
              └ {{ region.label }}
            </button>
            <button
              v-for="topic in topicsFor(region.value)"
              :key="topic.value"
              class="tree-grandchild"
              :class="{
                active:
                  category === root.id &&
                  industryRegion === region.value &&
                  industryTopic === topic.value,
              }"
              :aria-pressed="
                category === root.id &&
                industryRegion === region.value &&
                industryTopic === topic.value
              "
              :aria-label="region.label + ' / ' + topic.label"
              @click="filter(root.id, region.value, topic.value)"
            >
              └ {{ topic.label }}
            </button>
          </template>
        </template></template
      >
      <div class="tree-note">
        选择栏目后添加内容<br />发布结果自动显示在对应门户栏目
      </div>
    </aside>
    <div class="admin-body">
      <section class="admin-panel">
        <h2>条件检索</h2>
        <form
          class="filter-form"
          @submit.prevent="
            page = 1;
            load();
          "
        >
          <label
            >内容标题<input v-model="q" placeholder="输入标题关键词"
          /></label>
          <PublishStatusSelect v-model="status" />
          <button class="btn primary" :disabled="busy">⌕ 查询</button>
        </form>
      </section>
      <section class="admin-panel">
        <h2>{{ currentColumn }} · 内容管理</h2>
        <div class="table-toolbar">
          <button class="btn primary" @click="add">＋ 添加内容</button
          ><button
            class="btn success"
            @click="batch('publish')"
            :disabled="busy"
          >
            批量发布</button
          ><button
            class="btn warning"
            @click="batch('withdraw')"
            :disabled="busy"
          >
            批量撤回</button
          ><button
            class="btn"
            @click="
              status = status === 'DELETED' ? '' : 'DELETED';
              page = 1;
              load();
            "
          >
            {{ status === "DELETED" ? "返回有效内容" : "回收站" }}</button
          ><span>{{
            selected.length ? `已选择 ${selected.length} 条` : ""
          }}</span>
        </div>
        <p
          v-if="
            category === 'cooperation' ||
            categories.find((c) => c.id === category)?.parent_id ===
              'cooperation'
          "
          class="cooperation-admin-hint"
        >
          交流合作按子栏目发布。编辑文章时选择具体分类，并从文章图片中选择首页分类图；发布后图片和“更多”都会进入该分类列表。旧文章如仍属于“交流合作”，请编辑后补选分类。
        </p>
        <div v-if="error" class="inline-error">{{ error }}</div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="全选本页"
                    :checked="
                      items.length > 0 && selected.length === items.length
                    "
                    @change="
                      selected = ($event.target as HTMLInputElement).checked
                        ? items.map((i) => i.id)
                        : []
                    "
                  />
                </th>
                <th class="title-col">内容标题</th>
                <th>栏目</th>
                <th>更新时间</th>
                <th>显示日期</th>
                <th>展示优先级</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>
                  <input
                    type="checkbox"
                    v-model="selected"
                    :value="item.id"
                    :aria-label="'选择' + item.title"
                  />
                </td>
                <td>
                  <strong>{{ item.title }}</strong
                  ><small v-if="item.hasChanges" class="changed-label"
                    >有未发布修改</small
                  >
                </td>
                <td>
                  {{ item.categoryName
                  }}<small
                    v-if="item.industryContent"
                    class="industry-admin-label"
                    >{{ industryLabel(item) }}<br /><span
                      v-if="item.industryRecent"
                      >近期要闻 </span
                    ><span v-if="item.industryFeatured">图片推荐</span></small
                  >
                  <small
                    v-if="item.categoryId === 'cooperation'"
                    class="changed-label"
                    >待归入子栏目</small
                  >
                </td>
                <td>{{ date(item.updatedAt) }}</td>
                <td>{{ item.displayDate }}</td>
                <td>
                  {{ item.sort ?? 0
                  }}<small v-if="item.pinned" class="changed-label">置顶</small>
                </td>
                <td>
                  <span class="status" :class="item.status.toLowerCase()">{{
                    (
                      {
                        PUBLISHED: "已发布",
                        DRAFT: "草稿",
                        DELETED: "已删除",
                      } as Row
                    )[item.status]
                  }}</span>
                </td>
                <td>
                  <div class="row-actions">
                    <template v-if="item.status !== 'DELETED'"
                      ><RouterLink :to="'/operator/edit/' + item.id"
                        >编辑</RouterLink
                      ><button
                        v-if="item.status === 'DRAFT' || item.hasChanges"
                        @click="action(item, 'publish')"
                      >
                        发布</button
                      ><button
                        v-if="item.status === 'PUBLISHED'"
                        @click="action(item, 'withdraw')"
                      >
                        撤回</button
                      ><button
                        class="danger-text"
                        @click="action(item, 'delete')"
                      >
                        删除
                      </button></template
                    ><button v-else @click="action(item, 'restore')">
                      恢复
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!items.length">
                <td colspan="8" class="empty">
                  {{
                    busy ? "正在加载…" : "暂无内容，选择栏目后添加第一篇内容"
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <span>共 {{ total }} 条，每页 10 条</span
          ><button
            :disabled="page <= 1 || busy"
            @click="
              page--;
              load();
            "
          >
            上一页</button
          ><b>{{ page }}</b
          ><button
            :disabled="page * 10 >= total || busy"
            @click="
              page++;
              load();
            "
          >
            下一页
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.cooperation-admin-hint {
  margin: 0 20px 20px;
  padding: 14px 16px;
  border-radius: 6px;
  background: #f0f6ff;
  color: #42638d;
  font-size: 13px;
  line-height: 1.8;
}
</style>
