<script setup lang="ts">
import { categoryPath } from "../api";
import resourceNetworkImage from "../assets/汇聚生物医用材料数据网络.webp";
</script>

<template>
  <div class="home-resource-overview">
    <RouterLink
      :to="categoryPath('resources')"
      class="home-resource-image-link"
      aria-label="查看全部数据资源"
    >
      <img
        :src="resourceNetworkImage"
        alt="汇聚多源数据，赋能生物医用材料创新"
      />
    </RouterLink>
  </div>
</template>
