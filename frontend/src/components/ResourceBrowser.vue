<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { categoryPath } from "../api";
import {
  directories,
  directoriesLoading,
  directoriesError,
  loadDirectories,
  directoryPath,
  type Directory,
} from "../resources";
import ResourceTreeNode from "./ResourceTreeNode.vue";
import "../styles/resources.css";

const route = useRoute();
const router = useRouter();
const directorySearch = ref("");
const expanded = ref(new Set<string>());
const queried = ref(false);
const filters = reactive({ datasetName: "", templateName: "", creator: "" });
const selectedId = computed(() => String(route.query.directory || ""));
const path = computed(() => directoryPath(directories.value, selectedId.value));
const selected = computed(() => path.value.at(-1));
const missing = computed(
  () =>
    !directoriesLoading.value &&
    !directoriesError.value &&
    selectedId.value &&
    !selected.value,
);

function filterTree(nodes: Directory[], keyword: string): Directory[] {
  return nodes.flatMap((node) => {
    if (node.name.toLocaleLowerCase().includes(keyword)) return [node];
    const children = filterTree(node.children, keyword);
    return children.length ? [{ ...node, children }] : [];
  });
}
const filtered = computed(() => {
  const keyword = directorySearch.value.trim().toLocaleLowerCase();
  return keyword ? filterTree(directories.value, keyword) : directories.value;
});
function branchIds(nodes: Directory[]): string[] {
  return nodes.flatMap((node) =>
    node.children.length ? [node.id, ...branchIds(node.children)] : [],
  );
}
let initialized = false;
watch(directories, (nodes) => {
  if (!initialized && nodes.length) {
    expanded.value = new Set(nodes.map((node) => node.id));
    initialized = true;
  }
});
watch(
  path,
  (nodes) => {
    expanded.value = new Set([
      ...expanded.value,
      ...nodes.slice(0, -1).map((node) => node.id),
    ]);
  },
  { immediate: true },
);
watch(filtered, (nodes) => {
  if (directorySearch.value.trim()) expanded.value = new Set(branchIds(nodes));
  else
    expanded.value = new Set([
      ...expanded.value,
      ...path.value.slice(0, -1).map((node) => node.id),
    ]);
});
watch(
  () => route.query,
  (query) => {
    filters.datasetName = String(query.datasetName || "");
    filters.templateName = String(query.templateName || "");
    filters.creator = String(query.creator || "");
  },
  { immediate: true },
);
function toggle(id: string) {
  const next = new Set(expanded.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  expanded.value = next;
}
function selectDirectory(id: string) {
  const query = { ...route.query };
  if (id) query.directory = id;
  else delete query.directory;
  queried.value = false;
  void router.push({ path: categoryPath("resources"), query });
}
function queryDatasets() {
  const query = { ...route.query };
  for (const key of Object.keys(filters) as (keyof typeof filters)[]) {
    const value = filters[key].trim();
    if (value) query[key] = value;
    else delete query[key];
  }
  queried.value = true;
  void router.push({ path: categoryPath("resources"), query });
}
function resetFilters() {
  filters.datasetName = "";
  filters.templateName = "";
  filters.creator = "";
  queried.value = false;
  const query = { ...route.query };
  for (const key of Object.keys(filters)) delete query[key];
  void router.push({ path: categoryPath("resources"), query });
}
onMounted(() => {
  if (directories.value.length) {
    initialized = true;
    expanded.value = new Set([
      ...expanded.value,
      ...directories.value.map((node) => node.id),
    ]);
  }
  void loadDirectories();
});
</script>

<template>
  <div class="resource-browser">
    <div class="resource-browser-path">
      <nav aria-label="当前位置">
        <span>当前位置：</span><RouterLink to="/">首页</RouterLink>
        <span aria-hidden="true">&gt;</span>
        <h1>数据资源</h1>
      </nav>
    </div>
    <div class="resource-browser-layout">
      <aside
        class="resource-directory-panel"
        aria-label="科学分类目录"
        :aria-busy="directoriesLoading"
      >
        <div class="resource-directory-tools">
          <div class="resource-directory-search">
            <input
              v-model="directorySearch"
              type="search"
              placeholder="请输入科学分类名称"
              aria-label="搜索科学分类"
            />
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle
                cx="10"
                cy="10"
                r="6"
                stroke="currentColor"
                stroke-width="1.5"
              />
              <path d="m15 15 5 5" stroke="currentColor" stroke-width="1.5" />
            </svg>
          </div>
          <button
            type="button"
            class="resource-tree-fold"
            :aria-label="expanded.size ? '收起全部目录' : '展开全部目录'"
            :title="expanded.size ? '收起全部目录' : '展开全部目录'"
            :disabled="!filtered.length"
            @click="
              expanded = expanded.size
                ? new Set()
                : new Set(branchIds(filtered))
            "
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M8 5h13M8 12h13M8 19h13"
                stroke="currentColor"
                stroke-width="1.5"
              />
              <path
                :d="expanded.size ? 'm5 9-3 3 3 3' : 'm2 9 3 3-3 3'"
                stroke="currentColor"
                stroke-width="1.5"
              />
            </svg>
          </button>
        </div>
        <div
          v-if="directoriesLoading"
          class="resource-directory-message"
          role="status"
        >
          正在加载目录…
        </div>
        <div
          v-else-if="directoriesError"
          class="resource-directory-message"
          role="status"
        >
          <p>{{ directoriesError }}</p>
          <button type="button" @click="loadDirectories(true)">重新加载</button>
        </div>
        <template v-else>
          <button
            type="button"
            class="resource-all"
            :class="{ selected: !selectedId }"
            :aria-current="!selectedId ? 'true' : undefined"
            @click="selectDirectory('')"
          >
            全部
          </button>
          <div class="resource-tree-scroll">
            <ul v-if="filtered.length" class="resource-tree-list">
              <ResourceTreeNode
                v-for="node in filtered"
                :key="node.id"
                :node="node"
                :expanded="expanded"
                :selected-id="selectedId"
                @select="selectDirectory"
                @toggle="toggle"
              />
            </ul>
            <p v-else class="resource-directory-message" role="status">
              {{
                directorySearch.trim() ? "没有匹配的科学分类" : "暂无资源目录"
              }}
            </p>
          </div>
        </template>
      </aside>
      <section class="resource-dataset-panel" aria-label="数据集列表">
        <form class="resource-dataset-filters" @submit.prevent="queryDatasets">
          <label
            ><span>数据集名称</span
            ><input
              v-model="filters.datasetName"
              placeholder="请输入数据集名称"
              maxlength="100"
          /></label>
          <label
            ><span>产业分类</span
            ><select disabled aria-describedby="resource-data-hint">
              <option>请选择产业分类</option>
            </select></label
          >
          <label
            ><span>数据类别</span
            ><select disabled aria-describedby="resource-data-hint">
              <option>请选择数据类别</option>
            </select></label
          >
          <label
            ><span>模板名称</span
            ><input
              v-model="filters.templateName"
              placeholder="请输入模板名称"
              maxlength="100"
          /></label>
          <label
            ><span>创建人</span
            ><input
              v-model="filters.creator"
              placeholder="请输入创建人"
              maxlength="100"
          /></label>
          <label
            ><span>所属部门</span
            ><select disabled aria-describedby="resource-data-hint">
              <option>请选择</option>
            </select></label
          >
          <div class="resource-filter-actions">
            <button type="submit" class="resource-query">查询</button
            ><button type="button" @click="resetFilters">重置</button>
          </div>
        </form>
        <div class="resource-selection" aria-live="polite">
          <span v-if="missing" class="resource-missing"
            >该目录不存在或已更新，请重新选择。</span
          >
          <span
            v-else-if="selected"
            :title="path.map((node) => node.name).join(' / ')"
            >当前分类：{{ selected.name }}</span
          >
          <span id="resource-data-hint"
            >当前仅展示目录，暂无数据集记录及分类筛选项。</span
          >
        </div>
        <div class="resource-table-scroll">
          <table class="resource-dataset-table" aria-label="数据集查询结果">
            <thead>
              <tr>
                <th scope="col">序号</th>
                <th scope="col">数据集名称</th>
                <th scope="col">科学分类</th>
                <th scope="col">产业分类</th>
                <th scope="col">产品代码</th>
                <th scope="col">数据级别</th>
                <th scope="col">数据量</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td colspan="8" class="resource-no-data">
                  <span role="status">{{
                    queried ? "查询完成，暂无数据" : "暂无数据"
                  }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="resource-dataset-pagination">
          <span>共 <b>0</b> 条</span>
          <nav aria-label="数据集分页">
            <button disabled>上一页</button
            ><span class="current" aria-current="page">1</span
            ><button disabled>下一页</button>
          </nav>
        </div>
      </section>
    </div>
  </div>
</template>
