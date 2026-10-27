<script setup lang="ts">
import ResourceCatalog from "./ResourceCatalog.vue";
import { categoryPath } from "../api";
</script>
<template>
  <div class="home-resource-overview">
    <RouterLink
      :to="categoryPath('resources')"
      class="resource-network"
      aria-label="查看全部数据资源目录"
    >
      <svg viewBox="0 0 880 300" fill="none" aria-hidden="true">
        <defs>
          <linearGradient
            id="network-blue"
            x1="440"
            y1="76"
            x2="440"
            y2="222"
            gradientUnits="userSpaceOnUse"
          >
            <stop stop-color="#35b9ee" />
            <stop offset="1" stop-color="#196cc9" />
          </linearGradient>
          <linearGradient id="network-fade">
            <stop stop-color="#c9e9ff" stop-opacity="0" />
            <stop offset=".5" stop-color="#65b7f3" />
            <stop offset="1" stop-color="#c9e9ff" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path d="M78 220Q440-45 802 220" stroke="#abcbeb" stroke-width="3" />
        <path d="M120 230Q440 25 760 230" stroke="#b9dcf7" stroke-width="2" />
        <ellipse
          cx="440"
          cy="236"
          rx="308"
          ry="43"
          stroke="url(#network-fade)"
          stroke-width="3"
        />
        <ellipse
          cx="440"
          cy="236"
          rx="236"
          ry="29"
          fill="#e3f4ff"
          stroke="#77c5f8"
          stroke-width="2"
        />
        <path
          d="m440 175-184 52m184-52 183 52m-183-52-84 69m84-69 83 69"
          stroke="#71aada"
          stroke-dasharray="5 5"
          stroke-width="2"
        />
        <path
          d="M440 85c-25 0-45 20-45 44 0 34 45 83 45 83s45-49 45-83c0-24-20-44-45-44Z"
          fill="url(#network-blue)"
          stroke="white"
          stroke-width="5"
        />
        <path
          d="m421 129 19-11 19 11v22l-19 11-19-11v-22Zm0 0 19 11 19-11m-19 11v22"
          stroke="white"
          stroke-width="2"
        />
        <g fill="#f58939" stroke="white" stroke-width="3">
          <path
            d="M256 195c-11 0-19 8-19 18 0 15 19 37 19 37s19-22 19-37c0-10-8-18-19-18Z"
          />
          <path
            d="M356 216c-9 0-16 7-16 16 0 13 16 31 16 31s16-18 16-31c0-9-7-16-16-16Z"
          />
          <path
            d="M523 216c-9 0-16 7-16 16 0 13 16 31 16 31s16-18 16-31c0-9-7-16-16-16Z"
          />
          <path
            d="M623 195c-11 0-19 8-19 18 0 15 19 37 19 37s19-22 19-37c0-10-8-18-19-18Z"
          />
        </g>
      </svg>
      <span class="network-heading">材料数据资源</span>
      <span class="network-caption">开放共享 · 协同创新</span>
      <span class="network-label network-label-left">材料科学</span>
      <span class="network-label network-label-right">材料应用</span>
    </RouterLink>
    <ResourceCatalog preview />
  </div>
</template>
