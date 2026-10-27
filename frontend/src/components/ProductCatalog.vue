<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, type Row } from "../api";
import { productCategories, productTags } from "../products";
import HomeCrystalArt from "./HomeCrystalArt.vue";
import "../styles/products.css";

const props = defineProps<{ category: Row }>();
const route = useRoute(),
  router = useRouter();
const items = ref<Row[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref("");
const search = ref("");
const selectedCategory = computed(() =>
  String(route.query.productCategory || ""),
);
const selectedTag = computed(() => String(route.query.tag || ""));
const sort = computed(() => String(route.query.sort || "default"));
const page = computed(() => Math.max(1, Number(route.query.page) || 1));
const sorts = [
  { value: "default", label: "默认" },
  { value: "date", label: "按发布时间" },
  { value: "title", label: "按名称排序" },
  { value: "category", label: "按分类排序" },
];
let requestId = 0;
async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = "";
  search.value = String(route.query.q || "");
  try {
    const data = await api(
      "/api/public/contents?" +
        new URLSearchParams({
          category: props.category.id,
          q: search.value,
          productCategory: selectedCategory.value,
          tag: selectedTag.value,
          sort: sort.value,
          page: String(page.value),
        }),
    );
    if (request !== requestId) return;
    items.value = data.items;
    total.value = data.total;
  } catch (e) {
    if (request === requestId) error.value = (e as Error).message;
  } finally {
    if (request === requestId) loading.value = false;
  }
}
function filter(values: Record<string, string | undefined>) {
  router.push({
    path: route.path,
    query: { ...route.query, page: undefined, ...values },
  });
}
watch(() => [route.fullPath, props.category.id], load, { immediate: true });
onBeforeUnmount(() => requestId++);
</script>

<template>
  <div class="container product-catalog">
    <nav class="breadcrumbs" aria-label="当前位置">
      <span>当前位置：</span><RouterLink to="/">首页</RouterLink><span> / </span
      ><span aria-current="page">{{ category.name }}</span>
    </nav>
    <div class="product-catalog-layout">
      <aside class="product-sidebar">
        <h1><span aria-hidden="true">⬡</span> 数据产品</h1>
        <nav aria-label="产品分类">
          <button
            :class="{ active: !selectedCategory }"
            :aria-pressed="!selectedCategory"
            @click="filter({ productCategory: undefined })"
          >
            全部
          </button>
          <button
            v-for="name in productCategories"
            :key="name"
            :class="{ active: selectedCategory === name }"
            :aria-pressed="selectedCategory === name"
            @click="filter({ productCategory: name })"
          >
            {{ name }}
          </button>
        </nav>
      </aside>
      <section class="product-store" aria-label="应用商店">
        <div class="product-store-banner">
          <div>
            <h2>应用商店</h2>
            <p>汇聚材料数据产品，助力科研创新</p>
          </div>
          <HomeCrystalArt />
        </div>
        <div class="product-search-row">
          <form
            class="product-search"
            @submit.prevent="filter({ q: search.trim() || undefined })"
          >
            <input
              v-model="search"
              maxlength="200"
              aria-label="搜索数据产品"
              placeholder="请输入产品名称或关键词"
            />
            <button type="submit">
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle
                  cx="10.5"
                  cy="10.5"
                  r="6.5"
                  stroke="currentColor"
                  stroke-width="1.6"
                />
                <path
                  d="m16 16 5 5"
                  stroke="currentColor"
                  stroke-width="1.6"
                /></svg
              >搜索
            </button>
          </form>
        </div>
        <div class="product-toolbar">
          <div class="product-tag-filter" aria-label="产品标签">
            <span>标签：</span>
            <button
              :class="{ active: !selectedTag }"
              :aria-pressed="!selectedTag"
              @click="filter({ tag: undefined })"
            >
              全部
            </button>
            <button
              v-for="tag in productTags"
              :key="tag"
              :class="{ active: selectedTag === tag }"
              :aria-pressed="selectedTag === tag"
              @click="filter({ tag })"
            >
              {{ tag }}
            </button>
          </div>
          <div class="product-sort" aria-label="产品排序">
            <span>排序：</span>
            <button
              v-for="option in sorts"
              :key="option.value"
              :class="{ active: sort === option.value }"
              :aria-pressed="sort === option.value"
              @click="filter({ sort: option.value })"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <div v-if="loading" class="empty" role="status">正在加载产品…</div>
        <div v-else-if="error" class="empty" role="alert">
          {{ error }} <button class="text-button" @click="load">重试</button>
        </div>
        <template v-else>
          <div class="product-store-grid">
            <RouterLink
              v-for="product in items"
              :key="product.id"
              :to="'/content/' + product.id"
              class="store-product-card"
            >
              <span v-if="product.productTag" class="store-product-tag">{{
                product.productTag
              }}</span>
              <div class="store-product-identity">
                <img
                  v-if="product.cover || product.homeImage"
                  :src="product.cover || product.homeImage"
                  :alt="product.title"
                  loading="lazy"
                />
                <span v-else class="store-product-icon" aria-hidden="true"
                  ><svg viewBox="0 0 80 80" fill="none">
                    <path
                      d="m40 8 28 16v32L40 72 12 56V24Z"
                      fill="#e5f2ff"
                      stroke="#3988dd"
                      stroke-width="2"
                    />
                    <path
                      d="m12 24 28 16 28-16M40 40v32M26 16l28 16v16"
                      stroke="#3988dd"
                      stroke-width="2"
                    />
                    <path
                      d="m28 34 12-7 12 7v14l-12 7-12-7Z"
                      fill="#2675cc"
                    /></svg
                ></span>
                <span class="store-product-category">{{
                  product.productCategory || "数据产品"
                }}</span>
              </div>
              <div class="store-product-copy">
                <h3>{{ product.title }}</h3>
                <p>
                  {{
                    product.excerpt ||
                    product.summary ||
                    "点击查看产品详情与使用说明"
                  }}
                </p>
                <div class="store-product-provider">
                  <span>提供单位：</span
                  >{{
                    product.source || product.department || "未填写提供单位"
                  }}
                </div>
                <div class="store-product-meta">
                  <time :datetime="product.displayDate"
                    >◷ {{ product.displayDate }}</time
                  ><span v-if="product.pinned">置顶</span
                  ><span class="store-product-detail">查看详情 →</span>
                </div>
              </div>
            </RouterLink>
          </div>
          <div v-if="!items.length" class="product-empty empty">
            <h3>暂无符合条件的产品</h3>
            <p>试试其他分类、标签或关键词</p>
            <button
              class="text-button"
              @click="router.push({ path: route.path })"
            >
              查看全部产品
            </button>
          </div>
          <div v-if="total" class="pagination">
            <span>共 {{ total }} 个产品</span
            ><button
              :disabled="page <= 1"
              @click="filter({ page: String(page - 1) })"
            >
              上一页</button
            ><span>{{ page }} / {{ Math.ceil(total / 12) }}</span
            ><button
              :disabled="page * 12 >= total"
              @click="filter({ page: String(page + 1) })"
            >
              下一页
            </button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>
