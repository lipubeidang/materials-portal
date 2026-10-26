<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { api, state, notify, type Row, categoryPath } from "../api";
const router = useRouter(),
  data = ref<Row>({ items: [], latest: [], statistics: [] }),
  search = ref(""),
  slide = ref(0),
  newsTab = ref("all");
let interval: ReturnType<typeof setInterval>;
const banners = computed(() => state.site.banners || []);
const current = computed(
  () =>
    banners.value[slide.value] || {
      title: state.site.name,
      description: "汇聚材料数据，连接创新未来",
    },
);
const news = computed(() =>
  data.value.items.filter((x: Row) =>
    newsTab.value === "all"
      ? x.categoryId === "news" ||
        state.categories.some(
          (c) => c.id === x.categoryId && c.parent_id === "news",
        )
      : x.categoryId === newsTab.value,
  ),
);
const notices = computed(() =>
  data.value.items.filter((x: Row) => x.categoryId === "notices").slice(0, 5),
);
const resources = computed(() =>
  data.value.items.filter((x: Row) => x.type === "RESOURCE").slice(0, 3),
);
const products = computed(() =>
  data.value.items.filter((x: Row) => x.type === "PRODUCT").slice(0, 3),
);
const industry = computed(() =>
  data.value.items.filter((x: Row) => x.categoryId === "industry").slice(0, 3),
);
onMounted(async () => {
  try {
    data.value = await api("/api/public/home");
  } catch (e) {
    notify((e as Error).message, true);
  }
  interval = setInterval(() => {
    if (banners.value.length)
      slide.value = (slide.value + 1) % banners.value.length;
  }, 7000);
});
onUnmounted(() => clearInterval(interval));
function find() {
  router.push({ path: "/search", query: { q: search.value } });
}
</script>
<template>
  <section class="hero">
    <div
      class="container hero-inner"
      :style="
        current.image
          ? {
              backgroundImage: `linear-gradient(90deg,rgba(6,24,61,.8),rgba(6,24,61,.05)),url(${current.image})`,
            }
          : {}
      "
    >
      <div class="hero-copy">
        <span class="eyebrow">材料 · 数据 · 创新</span>
        <h1>{{ current.title }}</h1>
        <p>{{ current.description }}</p>
        <a v-if="current.url" :href="current.url" class="hero-link"
          >探索更多 <span>→</span></a
        >
      </div>
      <svg
        v-if="!current.image"
        class="molecule"
        viewBox="0 0 520 330"
        aria-hidden="true"
      >
        <defs>
          <radialGradient id="orb">
            <stop stop-color="#fff" />
            <stop offset=".35" stop-color="#9bf5ff" />
            <stop offset="1" stop-color="#2989e5" />
          </radialGradient>
          <linearGradient id="plate" x2="1" y2="1">
            <stop stop-color="#43b4ff" stop-opacity=".3" />
            <stop offset="1" stop-color="#3493e0" stop-opacity="0" />
          </linearGradient>
        </defs>
        <ellipse
          cx="270"
          cy="285"
          rx="190"
          ry="29"
          fill="url(#plate)"
          stroke="#4186d9"
        />
        <ellipse
          cx="270"
          cy="285"
          rx="140"
          ry="18"
          fill="none"
          stroke="#70c7fa"
        />
        <path
          d="M105 255V50L360 23v215z"
          fill="url(#plate)"
          stroke="#4d9de9"
          stroke-width="2"
        />
        <path
          d="M128 230V72L337 49v171z"
          fill="none"
          stroke="#8dd8ff"
          opacity=".4"
        />
        <g stroke="#83d9fb" stroke-width="5">
          <path
            d="M182 103L254 77 310 132 269 201 190 186 154 141 182 103 269 201M154 141L310 132M254 77L190 186"
          />
        </g>
        <g fill="url(#orb)">
          <circle cx="182" cy="103" r="17" />
          <circle cx="254" cy="77" r="21" />
          <circle cx="310" cy="132" r="18" />
          <circle cx="269" cy="201" r="23" />
          <circle cx="190" cy="186" r="16" />
          <circle cx="154" cy="141" r="14" />
        </g>
        <g fill="#9edaff" opacity=".6">
          <circle cx="406" cy="77" r="3" />
          <circle cx="410" cy="220" r="4" />
          <circle cx="70" cy="152" r="3" />
          <circle cx="374" cy="130" r="2" />
        </g>
        <text x="367" y="181" fill="#9ad4ff" font-size="11" letter-spacing="3">
          MATERIALS
        </text>
        <text x="367" y="199" fill="#6ea3db" font-size="9" letter-spacing="2">
          DATA SCIENCE
        </text>
      </svg>
      <div class="slide-dots">
        <button
          v-for="(_, i) in banners"
          :key="i"
          :class="{ active: i === slide }"
          @click="slide = Number(i)"
          :aria-label="`第 ${Number(i) + 1} 张轮播`"
        ></button>
      </div>
    </div>
  </section>
  <div class="ticker">
    <div class="container">
      <strong>最新资讯</strong
      ><RouterLink
        v-for="n in data.latest.slice(0, 2)"
        :key="n.id"
        :to="'/content/' + n.id"
        >{{ n.title }}</RouterLink
      >
    </div>
  </div>
  <div class="container home-content">
    <div class="overview">
      <div>
        <form class="search-box" @submit.prevent="find">
          <span>⌕</span
          ><input
            v-model="search"
            placeholder="搜索资讯、公告与材料资源"
            aria-label="站内搜索"
          /><button>搜索</button>
        </form>
        <div class="statistics">
          <div v-for="(stat, i) in data.statistics" :key="stat.label">
            <span class="stat-symbol">{{
              ["▧", "◈", "⬡", "▥"][Number(i)]
            }}</span
            ><span
              ><b>{{ stat.value }}</b
              ><small>{{ stat.label }}</small></span
            >
          </div>
        </div>
      </div>
      <div class="quick-links">
        <a
          v-for="link in state.site.links?.slice(0, 2)"
          :key="link.title"
          :href="link.url"
          ><span>◈</span><strong>{{ link.title }}</strong
          ><small>{{ link.description }}</small></a
        >
      </div>
    </div>
    <div class="home-columns">
      <section>
        <div class="section-heading">
          <h2>新闻中心 <small>NEWS CENTER</small></h2>
          <RouterLink :to="categoryPath('news')">更多 ＋</RouterLink>
        </div>
        <div class="news-grid">
          <RouterLink
            v-if="news[0]"
            :to="'/content/' + news[0].id"
            class="feature-news"
            ><div
              class="feature-art"
              :style="
                news[0].cover
                  ? { backgroundImage: `url(${news[0].cover})` }
                  : {}
              "
            >
              <template v-if="!news[0].cover"
                ><span class="art-lines"></span
                ><span>材料创新<br /><b>从数据开始</b></span
                ><small>MATERIALS INNOVATION</small></template
              >
            </div>
            <h3>{{ news[0].title }}</h3>
            <time>◷ {{ news[0].displayDate }}</time></RouterLink
          >
          <div>
            <div class="tabs">
              <button
                :class="{ active: newsTab === 'all' }"
                @click="newsTab = 'all'"
              >
                全部
              </button>
              <button
                v-for="cat in state.categories.filter(
                  (c) => c.parent_id === 'news',
                )"
                :key="cat.id"
                :class="{ active: newsTab === cat.id }"
                @click="newsTab = cat.id"
              >
                {{ cat.name }}
              </button>
            </div>
            <RouterLink
              v-for="n in news.slice(0, 5)"
              :key="n.id"
              :to="'/content/' + n.id"
              class="news-row"
              ><span>{{ n.title }}</span
              ><time>{{ n.displayDate }}</time></RouterLink
            >
            <div class="empty" v-if="!news.length">此栏目暂无首页推荐内容</div>
          </div>
        </div>
      </section>
      <section>
        <div class="section-heading">
          <h2>通知公告 <small>NOTICE BOARD</small></h2>
          <RouterLink :to="categoryPath('notices')">更多 ＋</RouterLink>
        </div>
        <RouterLink
          class="notice-row"
          v-for="n in notices"
          :key="n.id"
          :to="'/content/' + n.id"
          ><span class="date-card"
            ><b>{{ n.displayDate.slice(8) }}</b
            ><small>{{ n.displayDate.slice(0, 7) }}</small></span
          ><span>{{ n.title }}</span></RouterLink
        >
        <p v-if="!notices.length" class="empty">暂无公告</p>
      </section>
    </div>
    <div class="home-columns">
      <section>
        <div class="section-heading">
          <h2>数据资源 <small>DATA RESOURCES</small></h2>
          <RouterLink :to="categoryPath('resources')">更多 ＋</RouterLink>
        </div>
        <div class="resource-map">
          <span class="map-caption">开放 · 共享 · 协同</span>
          <div class="map-orbit orbit-one"></div>
          <div class="map-orbit orbit-two"></div>
          <div class="map-center">材料数据<br /><b>资源网络</b></div>
          <RouterLink
            v-for="(item, i) in resources"
            :key="item.id"
            :to="'/content/' + item.id"
            :class="'map-node node-' + i"
            >{{ item.title.replace("[演示] ", "") }}</RouterLink
          ><span class="map-tag tag-one">先进结构材料</span
          ><span class="map-tag tag-two">功能材料</span
          ><span class="map-tag tag-three">生物医用材料</span>
        </div>
      </section>
      <section>
        <div class="section-heading">
          <h2>数据产品 <small>PRODUCTS & SERVICES</small></h2>
          <RouterLink :to="categoryPath('products')">更多 ＋</RouterLink>
        </div>
        <RouterLink
          v-for="n in products"
          :key="n.id"
          :to="'/content/' + n.id"
          class="product-row"
          ><img v-if="n.cover" :src="n.cover" alt="" /><span
            v-else
            class="product-icon"
            >⬡</span
          >
          <div>
            <h3>{{ n.title }}</h3>
            <p>{{ n.excerpt || n.summary }}</p>
            <time>{{ n.displayDate }}</time>
          </div></RouterLink
        >
        <p v-if="!products.length" class="empty">暂无产品介绍</p>
      </section>
    </div>
    <section class="industry-section">
      <div class="section-heading">
        <h2>领域动态 <small>INDUSTRY INSIGHTS</small></h2>
        <RouterLink :to="categoryPath('industry')">更多 ＋</RouterLink>
      </div>
      <div class="industry-cards">
        <RouterLink v-for="n in industry" :key="n.id" :to="'/content/' + n.id"
          ><span class="card-label">领域动态</span>
          <h3>{{ n.title }}</h3>
          <p>{{ n.excerpt || n.summary }}</p>
          <span>{{ n.displayDate }} <b>→</b></span></RouterLink
        >
      </div>
      <p v-if="!industry.length" class="empty">暂无领域动态</p>
    </section>
  </div>
  <section class="partners" v-if="state.site.partners?.length">
    <div class="container">
      <div class="section-heading">
        <h2>建设单位 <small>PARTNER ORGANIZATIONS</small></h2>
      </div>
      <div class="partner-list">
        <a
          v-for="p in state.site.partners"
          :key="p.title"
          :href="p.url || undefined"
          ><img v-if="p.image" :src="p.image" alt="" /><strong>{{
            p.title
          }}</strong></a
        >
      </div>
    </div>
  </section>
</template>
