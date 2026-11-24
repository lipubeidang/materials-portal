<script setup lang="ts">
import { computed } from "vue";
import { state } from "../api";
import footerLogo from "../assets/白新材料大数据中心白.png";
import publicSecurityIcon from "../assets/备案小图标.png";

type FooterPartner = {
  title: string;
  url?: string;
};

const defaultConstructionPartners: FooterPartner[] = [
  { title: "四川大学", url: "https://www.scu.edu.cn/" },
  { title: "西南交通大学", url: "https://www.swjtu.edu.cn/" },
  {
    title: "中国科学院上海硅酸盐研究所",
    url: "https://www.sic.ac.cn/",
  },
  { title: "北京科技大学", url: "https://www.ustb.edu.cn/" },
  {
    title: "西安交通大学第一附属医院",
    url: "https://www.dyyy.xjtu.edu.cn/",
  },
  {
    title: "中国人民解放军总医院第四医学中心",
    url: "https://www.plagh.com.cn/",
  },
  {
    title: "山西锦波生物医药股份有限公司",
    url: "https://www.sxjbswyy.com/",
  },
  {
    title: "嘉思特医疗器材(天津)股份有限公司",
    url: "https://www.justmedical.cn/",
  },
  { title: "同方知网数字科技有限公司", url: "https://www.cnki.net/" },
];
const operationPartners: FooterPartner[] = [
  { title: "四川大学材料基因工程研究中心" },
  {
    title: "四川省生物医学工程学会",
    url: "https://scsssa.org.cn/xuehui/?key=&p=3",
  },
];

const constructionPartners = computed<FooterPartner[]>(() => {
  const configuredPartners = (state.site.partners || []) as FooterPartner[];
  return (
    configuredPartners.length ? configuredPartners : defaultConstructionPartners
  ).filter((partner) => partner.title);
});
</script>
<template>
  <footer class="portal-footer">
    <div class="container">
      <div class="portal-footer-inner">
        <div class="portal-footer-brand">
          <a
            href="https://www.matbd.cn/"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="访问新材料大数据中心网站（新窗口）"
          >
            <img
              :src="footerLogo"
              width="342"
              height="97"
              alt="新材料大数据中心 National Materials Big Data Center"
            />
          </a>
          <RouterLink to="/">
            <strong>新一代生物医用材料数据资源节点</strong>
          </RouterLink>
        </div>
        <div class="portal-footer-partners-group">
          <nav class="portal-footer-partners" aria-label="建设单位">
            <strong class="portal-footer-partners-title">建设单位</strong>
            <ul>
              <li v-for="partner in constructionPartners" :key="partner.title">
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
          <nav class="portal-footer-partners" aria-label="运维单位">
            <strong class="portal-footer-partners-title">运维单位</strong>
            <ul>
              <li v-for="partner in operationPartners" :key="partner.title">
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
        </div>
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
            <span>版权所有：国家新材料大数据中心</span>
          </div>
        </div>
      </div>
    </div>
  </footer>
</template>
