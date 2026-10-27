<script setup lang="ts">
import { ref, onMounted } from "vue";
import { api } from "../api";

const statistics = ref<{ key: string; label: string; value: string | null }[]>([
  { key: "templateCount", label: "模板量", value: null },
  { key: "fragmentCount", label: "模板片段量", value: null },
  { key: "datasetCount", label: "数据集量", value: null },
  { key: "dataCount", label: "数据量", value: null },
  { key: "publicDataCount", label: "已发布数据量", value: null },
  { key: "underReleaseDataCount", label: "发布中数据量", value: null },
]);
const loading = ref(false),
  error = ref("");
const icons: Record<string, string> = {
  templateCount:
    "M12 6h20l8 8v28H12V6Zm20 0v10h8M19 23h14M19 29h14M19 35h9M7 12v33h27",
  fragmentCount:
    "M8 8h13v13H8V8Zm19 0h13v13H27V8ZM8 27h13v13H8V27Zm19 0h13v13H27V27Z",
  datasetCount:
    "m24 5 17 10v20L24 45 7 35V15L24 5ZM7 15l17 10 17-10M24 25v20m-9-35 18 10v20",
  dataCount:
    "M8 12c0-9 32-9 32 0s-32 9-32 0Zm0 0v24c0 9 32 9 32 0V12M8 24c0 9 32 9 32 0",
  publicDataCount:
    "M24 5 8 12v13c0 10 16 19 16 19s16-9 16-19V12L24 5Zm-8 19 6 6 11-13",
  underReleaseDataCount: "M24 5a19 19 0 1 0 0 38 19 19 0 0 0 0-38Zm0 8v12l8 5",
};
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await api<{ items: typeof statistics.value }>(
      "/api/public/resources/statistics",
    );
    statistics.value = data.items;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
function format(value: string | null) {
  return value === null ? "—" : value.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}
onMounted(load);
</script>

<template>
  <section
    class="resource-statistics"
    aria-label="数据资源统计"
    :aria-busy="loading"
  >
    <div class="statistics">
      <div v-for="stat in statistics" :key="stat.key">
        <svg
          class="stat-icon"
          viewBox="0 0 48 48"
          fill="none"
          aria-hidden="true"
        >
          <path
            :d="icons[stat.key]"
            stroke="currentColor"
            stroke-width="2.2"
            stroke-linejoin="round"
            stroke-linecap="round"
          />
        </svg>
        <span class="stat-copy"
          ><b>{{ format(stat.value) }}</b
          ><small>{{ stat.label }}</small></span
        >
      </div>
    </div>
    <p v-if="loading" class="resource-status" role="status">正在加载统计…</p>
    <p v-else-if="error" class="resource-status" role="status">
      {{ error }} <button type="button" @click="load">重试</button>
    </p>
  </section>
</template>
