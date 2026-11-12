<script setup lang="ts">
import { computed } from "vue";
import { state } from "../api";
import footerLogo from "../assets/footer-logo.png";
import publicSecurityIcon from "../assets/备案小图标.png";

type FooterPartner = {
  title: string;
  url?: string;
};

const defaultPartners: FooterPartner[] = [
  { title: "四川大学", url: "https://www.scu.edu.cn/" },
  {
    title: "四川省生物医学工程学会",
    url: "https://scsssa.org.cn/xuehui/?key=&p=3",
  },
  { title: "中国科学院上海硅酸盐研究所", url: "https://www.sic.ac.cn/" },
  { title: "西南交通大学", url: "https://www.swjtu.edu.cn/" },
  { title: "北京科技大学", url: "https://www.ustb.edu.cn/" },
  { title: "锦波生物", url: "https://www.sxjbswyy.com/" },
  { title: "嘉思特医疗", url: "https://www.justmedical.cn/" },
];

const partners = computed<FooterPartner[]>(() => {
  const configuredPartners = (state.site.partners || []) as FooterPartner[];
  return (
    configuredPartners.length ? configuredPartners : defaultPartners
  ).filter((partner) => partner.title);
});
</script>
<template>
  <footer class="portal-footer">
    <div class="container">
      <div class="portal-footer-inner">
        <RouterLink to="/" class="portal-footer-brand">
          <img
            :src="footerLogo"
            width="342"
            height="97"
            alt="新材料大数据中心 National Materials Big Data Center"
          />
          <strong>新一代生物医用材料数据资源节点</strong>
        </RouterLink>
        <nav class="portal-footer-partners" aria-label="建设单位">
          <strong class="portal-footer-partners-title">建设单位</strong>
          <ul>
            <li v-for="partner in partners" :key="partner.title">
              <a
                v-if="partner.url"
                :href="partner.url"
                target="_blank"
                rel="noopener noreferrer"
                :aria-label="`访问${partner.title}网站（新窗口）`"
              >
                {{ partner.title }}
              </a>
              <span v-else>{{ partner.title }}</span>
            </li>
          </ul>
        </nav>
        <div class="portal-footer-info">
          <span
            class="visit-count"
            role="status"
            title="每次打开或切换前台页面计 1 次访问"
          >
            累计访问量：<b>{{
              state.totalVisits === null
                ? "—"
                : state.totalVisits.toLocaleString("zh-CN")
            }}</b>
          </span>
          <div class="portal-footer-legal">
            <a
              class="public-security-registration"
              href="https://beian.mps.gov.cn/#/query/webSearch?code=51010702044422"
              target="_blank"
              rel="noopener noreferrer"
            >
              <img :src="publicSecurityIcon" alt="" aria-hidden="true" />
              <span>川公网安备51010702044422号</span>
            </a>
            <component
              v-if="state.site.registrationNumber"
              :is="state.site.registrationUrl ? 'a' : 'span'"
              :href="state.site.registrationUrl || undefined"
              :target="state.site.registrationUrl ? '_blank' : undefined"
              :rel="
                state.site.registrationUrl ? 'noopener noreferrer' : undefined
              "
              >{{ state.site.registrationNumber }}</component
            >
            <span>版权所有：新材料大数据中心</span>
          </div>
        </div>
      </div>
    </div>
  </footer>
</template>
