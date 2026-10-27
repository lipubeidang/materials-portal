<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { api, state, notify, type Row, categoryPath } from "../api";
import SlideCarousel from "../components/SlideCarousel.vue";
import ResourceStatistics from "../components/ResourceStatistics.vue";
import HomeScienceArt from "../components/HomeScienceArt.vue";
import HomeCrystalArt from "../components/HomeCrystalArt.vue";
import ConstructionPartners from "../components/ConstructionPartners.vue";
import HomeResourceOverview from "../components/HomeResourceOverview.vue";
import "../styles/home.css";

const router = useRouter();
const data = ref<Row>({ items: [], latest: [] });
const search = ref("");
const newsTab = ref("all");
let mounted = true;
const banners = computed<Row[]>(() =>
  state.site.banners?.length
    ? state.site.banners
    : [
        {
          title: state.site.name,
          description: "汇聚材料数据，连接创新未来",
        },
      ],
);
const newsCategories = computed(() =>
  state.categories.filter((c) => c.parent_id === "news"),
);
watch(
  newsCategories,
  (categories) => {
    if (!categories.some((c) => c.id === newsTab.value))
      newsTab.value = categories[0]?.id || "all";
  },
  { immediate: true },
);
const allNews = computed(() =>
  data.value.items.filter(
    (x: Row) =>
      x.categoryId === "news" ||
      newsCategories.value.some((c) => c.id === x.categoryId),
  ),
);
const news = computed(() =>
  allNews.value.filter(
    (x: Row) => newsTab.value === "all" || x.categoryId === newsTab.value,
  ),
);
const notices = computed(() =>
  data.value.items.filter((x: Row) => x.categoryId === "notices").slice(0, 4),
);
const products = computed(() =>
  data.value.items.filter((x: Row) => x.type === "PRODUCT").slice(0, 3),
);
const quickLinks = computed<Row[]>(() =>
  (state.site.links || [])
    .filter(
      (link: Row) =>
        !["全球材料数据库", "数据检索", "在线开发", "在线研发"].includes(
          link.title,
        ),
    )
    .slice(0, 2),
);
const partners = computed<Row[]>(() => state.site.partners || []);

onMounted(async () => {
  try {
    const result = await api("/api/public/home");
    if (mounted) data.value = result;
  } catch (e) {
    if (mounted) notify((e as Error).message, true);
  }
});
onUnmounted(() => {
  mounted = false;
});
function find() {
  router.push({ path: "/search", query: { q: search.value } });
}
</script>

<template>
  <div class="home-page">
    <SlideCarousel
      :count="banners.length"
      label="首页轮播"
      class="hero-carousel"
    >
      <template #default="{ index }">
        <div class="hero" :class="{ 'hero-has-image': banners[index].image }">
          <img
            v-if="banners[index].image"
            class="hero-slide-image"
            :src="banners[index].image"
            alt=""
          />
          <div v-if="banners[index].image" class="hero-image-shade"></div>
          <div class="container hero-inner">
            <div class="hero-copy">
              <span
                v-if="banners[index].eyebrow ?? '让数据，转化为材料创新能力'"
                class="eyebrow"
                >{{
                  banners[index].eyebrow ?? "让数据，转化为材料创新能力"
                }}</span
              >
              <h1 :title="banners[index].title">{{ banners[index].title }}</h1>
              <p>{{ banners[index].description }}</p>
              <a
                v-if="banners[index].url"
                :href="banners[index].url"
                class="hero-link"
                >{{ banners[index].buttonText || "探索更多" }}
                <span aria-hidden="true">→</span></a
              >
            </div>
            <template v-if="!banners[index].image"
              ><HomeScienceArt v-if="index % 2 === 0" /><HomeCrystalArt v-else
            /></template>
          </div>
        </div>
      </template>
    </SlideCarousel>
    <div class="ticker">
      <div class="container">
        <strong>最新：</strong>
        <RouterLink
          v-for="n in data.latest.slice(0, 2)"
          :key="n.id"
          :to="'/content/' + n.id"
          :title="n.title"
          >{{ n.title }}</RouterLink
        >
        <span v-if="!data.latest.length" class="ticker-empty"
          >汇聚材料资讯，共享创新成果</span
        >
      </div>
    </div>
    <div class="home-main">
      <div class="container home-content">
        <div class="overview" :class="{ 'overview-wide': !quickLinks.length }">
          <div class="overview-main">
            <form class="search-box" @submit.prevent="find">
              <input
                v-model="search"
                placeholder="请输入您要搜索的内容"
                aria-label="站内搜索"
              />
              <button>
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle
                    cx="10"
                    cy="10"
                    r="6.5"
                    stroke="currentColor"
                    stroke-width="1.7"
                  />
                  <path
                    d="m15 15 5 5"
                    stroke="currentColor"
                    stroke-width="1.7"
                  /></svg
                >搜索
              </button>
            </form>
            <ResourceStatistics />
          </div>
          <div
            v-if="quickLinks.length"
            class="quick-links"
            aria-label="快捷入口"
          >
            <a v-for="link in quickLinks" :key="link.title" :href="link.url">
              <img v-if="link.image" :src="link.image" alt="" />
              <svg v-else viewBox="0 0 48 48" fill="none" aria-hidden="true">
                <path
                  d="m24 5 16 9v20l-16 9-16-9V14L24 5Z"
                  fill="#e7f3ff"
                  stroke="currentColor"
                  stroke-width="2"
                />
                <path
                  d="m8 14 16 10 16-10M24 24v19m-8-33 16 9v19"
                  stroke="currentColor"
                  stroke-width="2"
                />
              </svg>
              <strong :title="link.title">{{ link.title }}</strong
              ><small v-if="link.description">{{ link.description }}</small>
            </a>
          </div>
        </div>
        <div class="home-columns home-news-columns">
          <section aria-labelledby="home-news-title">
            <div class="section-heading">
              <h2 id="home-news-title">
                新闻中心 <small>/ NEWS CENTER</small>
              </h2>
              <RouterLink :to="categoryPath('news')">+更多</RouterLink>
            </div>
            <div class="news-grid">
              <SlideCarousel
                v-if="news.length"
                :key="newsTab"
                :count="news.length"
                label="新闻图片轮播"
                class="news-carousel"
              >
                <template #default="{ index }">
                  <RouterLink
                    :to="'/content/' + news[index].id"
                    class="feature-news"
                    :aria-label="'阅读文章：' + news[index].title"
                  >
                    <div class="feature-art">
                      <img
                        v-if="news[index].homeImage || news[index].cover"
                        :src="news[index].homeImage || news[index].cover"
                        :alt="news[index].title"
                      />
                      <template v-else
                        ><span class="art-lines"></span
                        ><span>材料创新<br /><b>从数据开始</b></span
                        ><small>MATERIALS INNOVATION</small></template
                      >
                    </div>
                  </RouterLink>
                </template>
                <template #caption="{ index }">
                  <RouterLink
                    :to="'/content/' + news[index].id"
                    class="feature-news feature-caption"
                  >
                    <h3 :title="news[index].title">{{ news[index].title }}</h3>
                    <time>◷ {{ news[index].displayDate }}</time>
                  </RouterLink>
                </template>
              </SlideCarousel>
              <div v-else class="feature-news feature-placeholder">
                <div class="feature-art">
                  <span class="art-lines"></span
                  ><span>材料创新<br /><b>从数据开始</b></span
                  ><small>MATERIALS INNOVATION</small>
                </div>
                <p>关注材料科研与平台最新进展</p>
              </div>
              <div class="news-list">
                <div class="tabs" aria-label="新闻分类">
                  <button
                    v-if="!newsCategories.length"
                    class="active"
                    @click="newsTab = 'all'"
                  >
                    全部新闻
                  </button>
                  <button
                    v-for="cat in newsCategories"
                    :key="cat.id"
                    :class="{ active: newsTab === cat.id }"
                    :aria-pressed="newsTab === cat.id"
                    @click="newsTab = cat.id"
                  >
                    {{ cat.name }}
                  </button>
                </div>
                <RouterLink
                  v-for="n in news.slice(0, 4)"
                  :key="n.id"
                  :to="'/content/' + n.id"
                  class="news-row"
                  ><span :title="n.title">{{ n.title }}</span
                  ><time>◷ {{ n.displayDate }}</time></RouterLink
                >
                <div class="empty" v-if="!news.length">
                  此栏目暂无首页推荐内容
                </div>
              </div>
            </div>
          </section>
          <section aria-labelledby="home-notices-title">
            <div class="section-heading">
              <h2 id="home-notices-title">
                通知公告 <small>/ NOTICE BOARD</small>
              </h2>
              <RouterLink :to="categoryPath('notices')">+更多</RouterLink>
            </div>
            <div class="home-notices">
              <RouterLink
                class="notice-row"
                v-for="n in notices"
                :key="n.id"
                :to="'/content/' + n.id"
                ><span class="date-card"
                  ><b>{{ n.displayDate.slice(8) }}</b
                  ><small>{{ n.displayDate.slice(0, 7) }}</small></span
                ><span class="notice-title" :title="n.title">{{
                  n.title
                }}</span></RouterLink
              >
              <p v-if="!notices.length" class="empty">暂无公告</p>
            </div>
          </section>
        </div>
        <div class="home-columns home-resource-columns">
          <section aria-labelledby="home-resources-title">
            <div class="section-heading">
              <h2 id="home-resources-title">
                数据资源 <small>/ DATA RESOURCES</small>
              </h2>
              <RouterLink :to="categoryPath('resources')">+更多</RouterLink>
            </div>
            <HomeResourceOverview />
          </section>
          <section aria-labelledby="home-products-title">
            <div class="section-heading">
              <h2 id="home-products-title">
                数据产品 <small>/ PRODUCTS AND SERVICES</small>
              </h2>
              <RouterLink :to="categoryPath('products')">+更多</RouterLink>
            </div>
            <div class="home-products">
              <RouterLink
                v-for="n in products"
                :key="n.id"
                :to="'/content/' + n.id"
                class="product-row"
              >
                <img
                  v-if="n.homeImage || n.cover"
                  :src="n.homeImage || n.cover"
                  alt=""
                /><span v-else class="product-icon" aria-hidden="true">⬡</span>
                <div class="product-copy">
                  <h3 :title="n.title">{{ n.title }}</h3>
                  <p v-if="n.excerpt || n.summary">
                    {{ n.excerpt || n.summary }}
                  </p>
                  <div class="product-meta">
                    <span v-if="n.source">{{ n.source }}</span
                    ><time>◷ {{ n.displayDate }}</time>
                  </div>
                </div>
              </RouterLink>
              <p v-if="!products.length" class="empty">暂无产品介绍</p>
            </div>
          </section>
        </div>
      </div>
    </div>
    <section class="home-organizations">
      <div class="container">
        <div class="section-heading">
          <h2>建设单位</h2>
        </div>
        <div class="organization-panel">
          <ConstructionPartners :partners="partners" />
        </div>
      </div>
    </section>
  </div>
</template>
