import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import Home from "./pages/Home.vue";
import ContentPage from "./pages/ContentPage.vue";
import Login from "./pages/Login.vue";
import Account from "./pages/Account.vue";
import { initialize, state, notify, recordVisit } from "./api";
import "./style.css";
import "./styles/portal.css";
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (to, from) => {
    const resourceSlug = state.categories.find(
      (category) => category.id === "resources",
    )?.slug;
    if (
      to.path === from.path &&
      to.params.slug === resourceSlug &&
      resourceSlug
    )
      return false;
    return { top: 0 };
  },
  routes: [
    { path: "/", component: Home },
    { path: "/columns/:slug", component: ContentPage },
    { path: "/content/:id", component: ContentPage },
    { path: "/search", component: ContentPage },
    { path: "/login", component: Login },
    { path: "/register", component: Login },
    { path: "/account", component: Account, meta: { login: true } },
    {
      path: "/operator",
      component: () => import("./pages/Admin.vue"),
      meta: { operator: true },
    },
    {
      path: "/operator/edit/:id",
      component: () => import("./pages/Editor.vue"),
      meta: { operator: true },
    },
    {
      path: "/operator/settings/:tab",
      component: () => import("./pages/Settings.vue"),
      meta: { operator: true },
    },
    { path: "/:pathMatch(.*)*", component: ContentPage },
  ],
});
const ready = initialize();
router.beforeEach(async (to) => {
  await ready;
  if ((to.meta.login || to.meta.operator) && !state.user.authenticated)
    return "/login";
  if (to.meta.operator && state.user.role !== "OPERATOR") {
    notify("仅操作员可以访问内容管理", true);
    return "/";
  }
});
router.afterEach((to, from, failure) => {
  if (
    !failure &&
    !to.path.startsWith("/operator") &&
    (!from.matched.length ||
      to.fullPath.split("#")[0] !== from.fullPath.split("#")[0])
  )
    void recordVisit();
});
createApp(App).use(router).mount("#app");
