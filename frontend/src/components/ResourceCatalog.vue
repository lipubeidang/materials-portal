<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { categoryPath } from "../api";
import {
  directories,
  directoriesLoading,
  directoriesError,
  loadDirectories,
  directoryPath,
} from "../resources";

const props = defineProps<{ preview?: boolean }>();
const route = useRoute();
const selectedId = computed(() =>
  props.preview ? "" : String(route.query.directory || ""),
);
const path = computed(() => directoryPath(directories.value, selectedId.value));
const selected = computed(() => path.value.at(-1));
const visible = computed(() => selected.value?.children ?? directories.value);
const missing = computed(() => selectedId.value && !selected.value);
function link(id = "") {
  return {
    path: categoryPath("resources"),
    query: id ? { directory: id } : {},
  };
}
onMounted(() => loadDirectories());
</script>

<template>
  <section
    class="resource-catalog"
    :class="{ 'catalog-preview': preview }"
    aria-label="资源目录"
    :aria-busy="directoriesLoading"
  >
    <div v-if="directoriesLoading" class="empty" role="status">
      正在加载资源目录…
    </div>
    <div v-else-if="directoriesError" class="empty" role="status">
      {{ directoriesError }}
      <button type="button" @click="loadDirectories(true)">重新加载</button>
    </div>
    <template v-else>
      <nav v-if="!preview" class="directory-path" aria-label="目录层级">
        <RouterLink
          :to="link()"
          :aria-current="!selectedId ? 'page' : undefined"
          >全部目录</RouterLink
        >
        <template v-for="(node, index) in path" :key="node.id">
          <span aria-hidden="true">/</span>
          <RouterLink
            :to="link(node.id)"
            :aria-current="index === path.length - 1 ? 'page' : undefined"
            >{{ node.name }}</RouterLink
          >
        </template>
      </nav>
      <div v-if="missing" class="empty">
        该目录不存在或已更新，请返回全部目录查看。
      </div>
      <template v-else>
        <div v-if="!preview" class="catalog-heading">
          <h2>{{ selected?.name || "资源目录" }}</h2>
          <span v-if="visible.length"
            >{{ visible.length }} 个{{ selected ? "子" : "一级" }}目录</span
          >
        </div>
        <div v-if="visible.length" class="directory-grid">
          <RouterLink
            v-for="node in visible"
            :key="node.id"
            :to="link(node.id)"
            class="directory-card"
          >
            <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
              <path
                d="M3 9a3 3 0 0 1 3-3h7l3 4h10a3 3 0 0 1 3 3v11a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9Z"
                fill="currentColor"
                opacity=".13"
              />
              <path
                d="M3 13h26M3 9a3 3 0 0 1 3-3h7l3 4h10a3 3 0 0 1 3 3v11a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9Z"
                stroke="currentColor"
                stroke-width="1.5"
              />
            </svg>
            <span class="directory-copy">
              <strong :title="node.name">{{ node.name }}</strong>
              <small>{{
                node.children.length
                  ? `${node.children.length} 个子目录`
                  : "查看目录"
              }}</small>
            </span>
            <span class="directory-arrow" aria-hidden="true">→</span>
          </RouterLink>
        </div>
        <div v-else class="empty catalog-empty">
          {{ selected ? "该目录下暂无数据" : "暂无资源目录" }}
        </div>
      </template>
    </template>
  </section>
</template>
