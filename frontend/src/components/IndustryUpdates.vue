<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, type Row } from "../api";
import { industryRegions, topicsFor } from "../industry";
import SlideCarousel from "./SlideCarousel.vue";
import "../styles/industry.css";

const props = defineProps<{ category: Row }>();
const route = useRoute(),
  router = useRouter();
const region = computed(() =>
  route.query.region === "international" ? "international" : "domestic",
);
const topics = computed(() => topicsFor(region.value));
const topic = computed(() =>
  topics.value.some((t) => t.value === route.query.topic)
    ? String(route.query.topic)
    : topics.value[0]!.value,
);
const recentView = computed(
  () => region.value === "domestic" && route.query.view === "recent",
);
const regionLabel = computed(
  () => industryRegions.find((r) => r.value === region.value)!.label,
);
const page = computed(() =>
  Math.max(1, Math.floor(Number(route.query.page) || 1)),
);
const result = ref<Row>({
  featured: [],
  recent: { items: [], total: 0 },
  listing: { items: [], total: 0 },
});
const loading = ref(false),
  error = ref("");
let requestId = 0;
async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const data = await api(
      "/api/public/industry?" +
        new URLSearchParams({
          region: region.value,
          topic: topic.value,
          view: recentView.value ? "recent" : "categories",
          page: String(page.value),
        }),
    );
    if (request === requestId) result.value = data;
  } catch (e) {
    if (request === requestId) error.value = (e as Error).message;
  } finally {
    if (request === requestId) loading.value = false;
  }
}
function filter(query: Record<string, string | undefined>) {
  router.push({
    path: route.path,
    query: {
      ...route.query,
      page: undefined,
      ...query,
      startDate: undefined,
      endDate: undefined,
    },
  });
}
function switchRegion(value: string) {
  filter({
    region: value,
    topic: undefined,
    view: undefined,
  });
}
watch(() => route.fullPath, load, { immediate: true });
onBeforeUnmount(() => requestId++);
</script>

<template>
  <div class="container industry-updates">
    <nav class="breadcrumbs" aria-label="当前位置">
      <span>当前位置：</span><RouterLink to="/">首页</RouterLink><span>/</span
      ><RouterLink :to="route.path">{{ category.name }}</RouterLink
      ><span>/</span><span aria-current="page">{{ regionLabel }}</span>
    </nav>
    <nav class="industry-region-tabs" aria-label="动态区域">
      <button
        v-for="option in industryRegions"
        :key="option.value"
        :class="{ active: region === option.value }"
        :aria-pressed="region === option.value"
        @click="switchRegion(option.value)"
      >
        {{ option.label }}
      </button>
    </nav>
    <div
      v-if="region === 'domestic'"
      class="industry-domestic-top"
      :class="{ 'industry-recent-only': recentView }"
    >
      <div v-if="!recentView" class="industry-feature">
        <div v-if="loading" class="industry-feature-empty" role="status">
          正在加载图片推荐…
        </div>
        <SlideCarousel
          v-else-if="!error && result.featured.length"
          :count="result.featured.length"
          label="国内动态图片推荐"
          :key="result.featured.map((x: Row) => x.id).join(',')"
          class="industry-carousel"
        >
          <template #default="{ index }"
            ><RouterLink
              :to="'/content/' + result.featured[index].id"
              :aria-label="'阅读文章：' + result.featured[index].title"
              class="industry-feature-image"
              ><img
                :src="
                  result.featured[index].homeImage ||
                  result.featured[index].cover
                "
                :alt="result.featured[index].title" /></RouterLink
          ></template>
          <template #caption="{ index }"
            ><RouterLink
              :to="'/content/' + result.featured[index].id"
              class="industry-feature-caption"
              ><h2 :title="result.featured[index].title">
                {{ result.featured[index].title }}
              </h2>
              <time :datetime="result.featured[index].displayDate"
                >◷ {{ result.featured[index].displayDate }}</time
              ></RouterLink
            ></template
          >
        </SlideCarousel>
        <div v-else class="industry-feature-empty">
          <svg viewBox="0 0 80 80" fill="none" aria-hidden="true">
            <path
              d="m40 8 28 16v32L40 72 12 56V24Z"
              stroke="currentColor"
              stroke-width="2"
            />
            <path
              d="m12 24 28 16 28-16M40 40v32"
              stroke="currentColor"
              stroke-width="2"
            /></svg
          ><strong>关注材料领域新进展</strong><span>暂无图片推荐</span>
        </div>
      </div>
      <section class="industry-recent" aria-labelledby="industry-recent-title">
        <div class="industry-recent-heading">
          <h2 id="industry-recent-title">近期要闻</h2>
        </div>
        <template v-if="!recentView">
          <div v-if="loading" class="empty" role="status">
            正在加载近期要闻…
          </div>
          <template v-else-if="!error">
            <RouterLink
              v-for="article in result.recent.items"
              :key="article.id"
              :to="'/content/' + article.id"
              class="industry-recent-row"
              ><h3 :title="article.title">{{ article.title }}</h3>
              <time :datetime="article.displayDate"
                >◷ {{ article.displayDate }}</time
              ></RouterLink
            >
            <p v-if="!result.recent.items.length" class="empty">
              暂无近期要闻
            </p>
            <button
              v-if="result.recent.total"
              class="industry-more"
              @click="filter({ view: 'recent' })"
            >
              查看更多
            </button>
          </template>
        </template>
      </section>
    </div>
    <div v-if="recentView" class="industry-list-heading">
      <h2>全部近期要闻</h2>
      <button @click="filter({ view: undefined })">← 返回分类内容</button>
    </div>
    <nav v-else class="industry-topic-tabs" aria-label="动态内容分类">
      <button
        v-for="option in topics"
        :key="option.value"
        :class="{ active: topic === option.value }"
        :aria-pressed="topic === option.value"
        @click="filter({ topic: option.value })"
      >
        {{ option.label }}
      </button>
    </nav>
    <div v-if="loading" class="empty" role="status">正在加载动态…</div>
    <div v-else-if="error" class="empty" role="alert">
      {{ error }} <button class="text-button" @click="load">重试</button>
    </div>
    <template v-else>
      <div class="industry-articles">
        <RouterLink
          v-for="article in result.listing.items"
          :key="article.id"
          :to="'/content/' + article.id"
          class="industry-article-card"
        >
          <div class="industry-card-heading">
            <h2 :title="article.title">{{ article.title }}</h2>
            <span v-if="region === 'international'" class="industry-view"
              ><svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12Z"
                  stroke="currentColor"
                  stroke-width="1.5"
                />
                <circle
                  cx="12"
                  cy="12"
                  r="3"
                  stroke="currentColor"
                  stroke-width="1.5"
                /></svg
              >查看</span
            >
          </div>
          <div class="industry-card-meta">
            <span
              v-if="article.source || article.department"
              class="industry-source"
              :title="article.source || article.department"
              ><svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle
                  cx="12"
                  cy="7"
                  r="4"
                  stroke="currentColor"
                  stroke-width="1.3"
                />
                <path
                  d="M4 22v-3a8 8 0 0 1 16 0v3"
                  stroke="currentColor"
                  stroke-width="1.3"
                /></svg
              >{{ article.source || article.department }}</span
            ><time :datetime="article.displayDate"
              >◷ {{ article.displayDate }}</time
            >
          </div>
        </RouterLink>
      </div>
      <div
        v-if="!result.listing.items.length"
        class="empty industry-list-empty"
      >
        {{ recentView ? "暂无近期要闻" : "此分类暂无已发布动态" }}
      </div>
      <div v-if="result.listing.total" class="pagination">
        <span>共 {{ result.listing.total }} 条</span
        ><button
          :disabled="page <= 1"
          @click="filter({ page: String(page - 1) })"
        >
          上一页</button
        ><span>{{ page }} / {{ Math.ceil(result.listing.total / 12) }}</span
        ><button
          :disabled="page * 12 >= result.listing.total"
          @click="filter({ page: String(page + 1) })"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>
