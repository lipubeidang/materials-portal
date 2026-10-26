<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from "vue";
import { useRoute } from "vue-router";
import { api, state, type Row, categoryPath } from "../api";
import ArticleContent from "../components/ArticleContent.vue";

const route = useRoute(),
  item = ref<Row | null>(null),
  items = ref<Row[]>([]),
  total = ref(0),
  page = ref(1),
  error = ref(""),
  loading = ref(false),
  q = ref("");
const cat = computed(() =>
  state.categories.find((c) => c.slug === route.params.slug),
);
const currentCategory = computed(
  () =>
    state.categories.find((c) => c.id === item.value?.categoryId) || cat.value,
);
const parentCategory = computed(() =>
  state.categories.find((c) => c.id === currentCategory.value?.parent_id),
);
const categoryRoot = computed(() => parentCategory.value || cat.value);
const children = computed(() =>
  state.categories.filter((c) => c.parent_id === categoryRoot.value?.id),
);
const reading = computed(() => !!route.params.id || cat.value?.type === "PAGE");
const title = computed(() =>
  route.path === "/search" ? "站内搜索" : cat.value?.name || "内容详情",
);
const description = computed(() => {
  if (route.path === "/search") return "查找资讯、公告与材料资源";
  const descriptions: Record<string, string> = {
    news: "关注科研进展，分享平台资讯",
    notices: "及时了解平台通知与服务安排",
    industry: "追踪材料研究与产业应用动态",
    resources: "发现材料数据与科研资源",
    products: "了解数据产品与研究服务",
    cooperation: "连接研究需求，拓展交流合作",
  };
  return descriptions[categoryRoot.value?.id] || "浏览本栏目发布的内容";
});
let requestId = 0;
async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = "";
  item.value = null;
  items.value = [];
  state.contentCategoryId = cat.value?.id || "";
  try {
    if (route.params.id) {
      const data = await api("/api/public/contents/" + route.params.id);
      if (request !== requestId) return;
      item.value = data;
      state.contentCategoryId = data.categoryId;
    } else if (cat.value || route.path === "/search") {
      q.value = String(route.query.q || "");
      const data = await api(
        "/api/public/contents?" +
          new URLSearchParams({
            category: cat.value?.id || "",
            q: q.value,
            page: String(page.value),
          }),
      );
      if (request !== requestId) return;
      items.value = data.items;
      total.value = data.total;
      if (cat.value?.type === "PAGE" && items.value[0])
        item.value = items.value[0];
    } else error.value = "页面不存在";
  } catch (e) {
    if (request === requestId) error.value = (e as Error).message;
  } finally {
    if (request === requestId) loading.value = false;
  }
}
watch(
  () => route.fullPath,
  () => {
    page.value = 1;
    load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  requestId++;
  state.contentCategoryId = "";
});
function changePage(delta: number) {
  page.value += delta;
  load();
  window.scrollTo({ top: 0, behavior: "smooth" });
}
</script>

<template>
  <div v-if="!reading" class="page-banner collection-banner">
    <div class="container collection-heading">
      <div>
        <span>NEW MATERIALS DATA CENTER</span>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
      <span v-if="!loading && !error" class="collection-count"
        >{{ total }} <small>篇内容</small></span
      >
    </div>
  </div>
  <div class="container content-layout" :class="{ 'reading-layout': reading }">
    <div class="content-path">
      <nav class="breadcrumbs" aria-label="当前位置">
        <RouterLink to="/">首页</RouterLink><span aria-hidden="true">/</span>
        <template v-if="parentCategory">
          <RouterLink :to="categoryPath(parentCategory.id)">{{
            parentCategory.name
          }}</RouterLink
          ><span aria-hidden="true">/</span>
        </template>
        <template v-if="reading && currentCategory && route.params.id">
          <RouterLink :to="categoryPath(currentCategory.id)">{{
            currentCategory.name
          }}</RouterLink
          ><span aria-hidden="true">/</span>
          <span aria-current="page">正文</span>
        </template>
        <span v-else aria-current="page">{{ title }}</span>
      </nav>
      <RouterLink
        v-if="reading && currentCategory && route.params.id"
        :to="categoryPath(currentCategory.id)"
        class="back-to-list"
        >← 返回列表</RouterLink
      >
    </div>
    <section class="content-panel">
      <div v-if="loading" class="empty">正在加载…</div>
      <div v-else-if="error" class="empty">
        {{ error }} <RouterLink to="/">返回首页</RouterLink>
      </div>
      <ArticleContent v-else-if="item" :item="item" />
      <template v-else>
        <form
          v-if="route.path === '/search'"
          class="search-box collection-search"
          @submit.prevent="$router.push({ path: '/search', query: { q } })"
        >
          <input
            v-model="q"
            placeholder="搜索已发布内容"
            aria-label="搜索已发布内容"
          /><button>搜索</button>
        </form>
        <nav
          v-if="children.length && categoryRoot"
          class="category-filters"
          aria-label="子栏目筛选"
        >
          <RouterLink
            :to="categoryPath(categoryRoot.id)"
            :class="{ selected: cat?.id === categoryRoot.id }"
            >全部{{ categoryRoot.name }}</RouterLink
          >
          <RouterLink
            v-for="child in children"
            :key="child.id"
            :to="categoryPath(child.id)"
            :class="{ selected: cat?.id === child.id }"
            >{{ child.name }}</RouterLink
          >
        </nav>
        <div
          :class="
            cat?.template === 'cards'
              ? 'listing-cards'
              : cat?.template === 'date'
                ? 'listing date-listing'
                : 'listing'
          "
        >
          <RouterLink
            v-for="n in items"
            :key="n.id"
            :to="'/content/' + n.id"
            class="list-item"
          >
            <span v-if="cat?.template === 'date'" class="date-card"
              ><b>{{ n.displayDate.slice(8) }}</b
              ><small>{{ n.displayDate.slice(0, 7) }}</small></span
            >
            <img v-if="n.cover" :src="n.cover" :alt="n.title" loading="lazy" />
            <span
              v-else-if="cat?.template === 'cards'"
              class="card-art"
              aria-hidden="true"
              >◈</span
            >
            <div class="list-copy">
              <span class="article-category"
                >{{ n.categoryName }} <b v-if="n.pinned">置顶</b></span
              >
              <h2>{{ n.title }}</h2>
              <p v-if="n.excerpt || n.summary">{{ n.excerpt || n.summary }}</p>
              <time :datetime="n.displayDate">{{ n.displayDate }}</time>
            </div>
            <span class="arrow" aria-hidden="true">→</span>
          </RouterLink>
        </div>
        <div v-if="!items.length" class="empty">
          {{
            route.path === "/search"
              ? "没有找到匹配内容，试试其他关键词"
              : "暂无已发布内容"
          }}
        </div>
        <div v-if="total" class="pagination">
          <span>共 {{ total }} 条</span
          ><button :disabled="page <= 1" @click="changePage(-1)">上一页</button>
          <span>{{ page }} / {{ Math.ceil(total / 12) }}</span
          ><button :disabled="page * 12 >= total" @click="changePage(1)">
            下一页
          </button>
        </div>
      </template>
    </section>
  </div>
</template>
