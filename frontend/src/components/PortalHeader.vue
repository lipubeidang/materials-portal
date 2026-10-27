<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { state, logout, categoryPath } from "../api";
import portalLogo from "../assets/portal-logo.png";
const props = defineProps<{ home?: boolean }>();
const omittedHomeEntries = new Set([
  "数据检索",
  "在线开发",
  "在线研发",
  "全球材料数据库",
]);
const expanded = ref(false);
const route = useRoute();
const activeCategory = computed(() =>
  state.categories.find((c) =>
    route.params.id
      ? c.id === state.contentCategoryId
      : c.slug === route.params.slug,
  ),
);
function activeSection(id: string) {
  return (
    activeCategory.value?.id === id || activeCategory.value?.parent_id === id
  );
}
watch(
  () => route.fullPath,
  () => (expanded.value = false),
);
const roots = computed(() => {
  const categories = state.categories.filter(
    (c) =>
      !c.parent_id &&
      c.navigation &&
      (!props.home || !omittedHomeEntries.has(c.name)),
  );
  const newsIndex = categories.findIndex((c) => c.id === "news");
  if (newsIndex < 0) return categories;
  const dataEntries = ["resources", "products"]
    .map((id) => categories.find((c) => c.id === id))
    .filter((c) => c !== undefined);
  const ordered = categories.filter((c) => !dataEntries.includes(c));
  ordered.splice(
    ordered.findIndex((c) => c.id === "news") + 1,
    0,
    ...dataEntries,
  );
  return ordered;
});
const extraNavigation = computed(() =>
  (state.site.navigation || []).filter(
    (item: { title: string }) =>
      !props.home || !omittedHomeEntries.has(item.title),
  ),
);
function hasDropdown(id: string) {
  return (
    id !== "industry" &&
    id !== "resources" &&
    id !== "news" &&
    id !== "cooperation" &&
    state.categories.some((c) => c.parent_id === id)
  );
}
</script>
<template>
  <div class="brand-bar container">
    <RouterLink to="/" class="brand"
      ><img
        class="portal-logo"
        :src="portalLogo"
        alt="四川大学、四川大学生物医学工程学院、NEBM、四川省生物医学工程学会标识"
        width="1894"
        height="518"
      /><span class="brand-name"
        ><strong>{{ state.site.name }}</strong
        ><small>{{ state.site.englishName }}</small></span
      ></RouterLink
    >
    <div class="account-links">
      <template v-if="state.user.authenticated">
        <RouterLink
          to="/account"
          class="account-button"
          :title="state.user.name + ' · 个人中心'"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="8" r="3.5" />
            <path d="M5 21v-2a7 7 0 0 1 14 0v2" />
          </svg>
          <span class="account-name">{{ state.user.name }}</span>
        </RouterLink>
        <RouterLink
          v-if="state.user.role === 'OPERATOR'"
          to="/operator"
          class="account-button account-primary"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="3" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="3" width="7" height="7" rx="1" />
            <rect x="3" y="14" width="7" height="7" rx="1" />
            <rect x="14" y="14" width="7" height="7" rx="1" />
          </svg>
          <span>内容管理</span>
        </RouterLink>
        <button
          class="account-button account-exit"
          @click="logout"
          aria-label="退出登录"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M10 4H4v16h6M14 8l4 4-4 4M8 12h12" />
          </svg>
          <span>退出登录</span>
        </button>
      </template>
      <template v-else>
        <RouterLink to="/login" class="account-button account-primary"
          >登录</RouterLink
        >
        <RouterLink to="/register" class="account-button">注册</RouterLink>
      </template>
    </div>
  </div>
  <div class="navigation">
    <nav class="container" aria-label="主导航">
      <button
        class="mobile-menu"
        @click="expanded = !expanded"
        :aria-expanded="expanded"
        aria-controls="portal-navigation"
      >
        ☰ 栏目导航
      </button>
      <div id="portal-navigation" class="nav-items" :class="{ expanded }">
        <RouterLink to="/" @click="expanded = false">首页</RouterLink>
        <div class="nav-item" v-for="c in roots" :key="c.id">
          <RouterLink
            :to="categoryPath(c.id)"
            :class="{ 'active-section': activeSection(c.id) }"
            @click="expanded = false"
            >{{ c.name }}<span v-if="hasDropdown(c.id)">⌄</span></RouterLink
          >
          <div class="dropdown" v-if="hasDropdown(c.id)">
            <RouterLink
              v-for="child in state.categories.filter(
                (x) => x.parent_id === c.id,
              )"
              :key="child.id"
              :to="categoryPath(child.id)"
              >{{ child.name }}</RouterLink
            >
          </div>
        </div>
        <a
          v-for="item in extraNavigation"
          :key="item.title"
          :href="item.url"
          rel="noopener noreferrer"
          >{{ item.title }}</a
        >
      </div>
    </nav>
  </div>
</template>
