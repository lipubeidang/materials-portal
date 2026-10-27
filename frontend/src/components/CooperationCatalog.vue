<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, categoryPath, state, type Row } from "../api";
import "../styles/cooperation.css";

const props = defineProps<{ category: Row }>();
const route = useRoute();
const router = useRouter();
const overview = computed(() => props.category.id === "cooperation");
const root = computed(() =>
  state.categories.find((c) => c.id === "cooperation"),
);
const tiles = ref<Row[]>([]);
const legacyItems = ref<Row[]>([]);
const items = ref<Row[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref("");
const failedImages = ref(new Set<string>());
const page = computed(() => {
  const value = Number(route.query.page || 1);
  return Number.isSafeInteger(value) && value > 0 ? value : 1;
});
const pageSize = computed(() => {
  const value = Number(route.query.size || 10);
  return [10, 20, 50].includes(value) ? value : 10;
});
const pageCount = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize.value)),
);
let requestId = 0;
async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = "";
  failedImages.value = new Set();
  try {
    if (overview.value) {
      const data = await api("/api/public/cooperation");
      if (request !== requestId) return;
      tiles.value = data.categories;
      legacyItems.value = data.legacyItems;
    } else {
      const data = await api(
        "/api/public/contents?" +
          new URLSearchParams({
            category: props.category.id,
            page: String(page.value),
            size: String(pageSize.value),
          }),
      );
      if (request !== requestId) return;
      items.value = data.items;
      total.value = data.total;
      if (page.value > pageCount.value) {
        await router.replace({
          query: { ...route.query, page: String(pageCount.value) },
        });
      }
    }
  } catch (e) {
    if (request === requestId) error.value = (e as Error).message;
  } finally {
    if (request === requestId) loading.value = false;
  }
}
watch(() => [props.category.id, page.value, pageSize.value], load, {
  immediate: true,
});
onBeforeUnmount(() => requestId++);
function changePage(value: number) {
  router.push({ query: { ...route.query, page: String(value) } });
}
function changeSize(event: Event) {
  router.push({
    query: {
      ...route.query,
      size: (event.target as HTMLSelectElement).value,
      page: "1",
    },
  });
}
</script>

<template>
  <div class="container cooperation-page">
    <nav class="cooperation-breadcrumbs" aria-label="当前位置">
      <span>当前位置：</span>
      <RouterLink to="/">首页</RouterLink><span aria-hidden="true">&gt;</span>
      <span v-if="overview" aria-current="page">{{ category.name }}</span>
      <template v-else>
        <RouterLink :to="categoryPath('cooperation')">{{
          root?.name || "交流合作"
        }}</RouterLink>
        <span aria-hidden="true">&gt;</span>
        <span aria-current="page">{{ category.name }}</span>
      </template>
    </nav>

    <div v-if="loading" class="cooperation-message" role="status">
      正在加载…
    </div>
    <div v-else-if="error" class="cooperation-message" role="alert">
      <p>{{ error }}</p>
      <button class="btn" @click="load">重新加载</button>
    </div>
    <div v-else-if="overview" class="cooperation-overview">
      <aside class="cooperation-sidebar">
        <h1>
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.4"
            aria-hidden="true"
          >
            <circle cx="6" cy="5" r="2" />
            <circle cx="18" cy="5" r="2" />
            <path
              d="M3 19v-7a3 3 0 0 1 6 0v7H3Zm12 0v-7a3 3 0 0 1 6 0v7h-6ZM9 13h6m-5-6 2 2 2-2"
            />
          </svg>
          {{ category.name }}
        </h1>
        <nav aria-label="交流合作分类">
          <RouterLink
            v-for="tile in tiles"
            :key="tile.id"
            :to="categoryPath(tile.id)"
          >
            <span aria-hidden="true">▪</span>{{ tile.name }}
          </RouterLink>
        </nav>
      </aside>
      <div class="cooperation-main">
        <div class="cooperation-grid">
          <section
            v-for="tile in tiles"
            :key="tile.id"
            class="cooperation-tile"
          >
            <div class="cooperation-tile-heading">
              <h2>
                <RouterLink :to="categoryPath(tile.id)">{{
                  tile.name
                }}</RouterLink>
              </h2>
              <RouterLink
                class="cooperation-more"
                :to="categoryPath(tile.id)"
                :aria-label="'更多' + tile.name"
                >+更多</RouterLink
              >
            </div>
            <RouterLink
              class="cooperation-image"
              :to="categoryPath(tile.id)"
              :aria-label="'查看' + tile.name + '文章列表'"
            >
              <img
                v-if="tile.image && !failedImages.has(tile.id)"
                :src="tile.image"
                :alt="tile.imageAlt || tile.name"
                :style="{ objectFit: tile.imageFit }"
                loading="lazy"
                @error="failedImages.add(tile.id)"
              />
              <span v-else class="cooperation-image-empty">
                <svg
                  viewBox="0 0 48 48"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.5"
                  aria-hidden="true"
                >
                  <rect x="7" y="9" width="34" height="30" rx="3" />
                  <circle cx="17" cy="19" r="3" />
                  <path d="m8 34 10-9 8 7 6-5 9 9" />
                </svg>
                <strong>{{ tile.name }}</strong>
                <small>{{
                  tile.total ? "查看本栏目信息 →" : "暂无已发布信息"
                }}</small>
              </span>
            </RouterLink>
          </section>
        </div>
        <div v-if="!tiles.length" class="cooperation-message">
          暂无开放的交流合作分类
        </div>
        <section v-if="legacyItems.length" class="cooperation-legacy">
          <h2 class="cooperation-section-title">其他交流资讯</h2>
          <RouterLink
            v-for="article in legacyItems"
            :key="article.id"
            :to="'/content/' + article.id"
          >
            <span>{{ article.title }}</span
            ><time :datetime="article.displayDate">{{
              article.displayDate
            }}</time>
          </RouterLink>
        </section>
      </div>
    </div>

    <section v-else class="cooperation-list" :aria-label="category.name">
      <h1 class="cooperation-section-title">{{ category.name }}</h1>
      <div class="cooperation-list-toolbar">
        <span>共{{ total }}条信息</span>
        <select
          :value="pageSize"
          aria-label="每页显示条数"
          @change="changeSize"
        >
          <option v-for="size in [10, 20, 50]" :key="size" :value="size">
            每页显示条数({{ size }})
          </option>
        </select>
      </div>
      <div class="cooperation-articles">
        <RouterLink
          v-for="article in items"
          :key="article.id"
          :to="'/content/' + article.id"
          class="cooperation-article"
        >
          <h2>{{ article.title }}</h2>
          <div class="cooperation-article-meta">
            <span v-if="article.source || article.department">
              <svg
                viewBox="0 0 20 20"
                fill="none"
                stroke="currentColor"
                stroke-width="1.2"
                aria-hidden="true"
              >
                <circle cx="10" cy="6" r="3" />
                <path d="M3 18v-2a7 7 0 0 1 14 0v2" />
              </svg>
              {{ article.source || article.department }}
            </span>
            <time :datetime="article.displayDate">
              <svg
                viewBox="0 0 20 20"
                fill="none"
                stroke="currentColor"
                stroke-width="1.2"
                aria-hidden="true"
              >
                <circle cx="10" cy="10" r="7" />
                <path d="M10 5v5h4" />
              </svg>
              {{ article.displayDate.replaceAll("-", ".") }}
            </time>
          </div>
        </RouterLink>
      </div>
      <div v-if="!items.length" class="cooperation-message">暂无已发布信息</div>
      <nav
        v-if="total"
        class="pagination cooperation-pagination"
        aria-label="文章分页"
      >
        <button :disabled="page <= 1" @click="changePage(page - 1)">
          上一页
        </button>
        <span aria-live="polite">第 {{ page }} / {{ pageCount }} 页</span>
        <button :disabled="page >= pageCount" @click="changePage(page + 1)">
          下一页
        </button>
      </nav>
    </section>
  </div>
</template>
