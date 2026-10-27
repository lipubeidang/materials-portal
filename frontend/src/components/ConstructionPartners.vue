<script setup lang="ts">
import type { Row } from "../api";
import { useId } from "vue";
import reference from "../assets/construction-reference.png";

defineProps<{ partners: Row[] }>();
const clipPrefix = useId();
// Display the original supplied marks without redrawing or altering their pixels.
const referencePartners = [
  { title: "四川大学", viewBox: "94 127 154 55" },
  { title: "四川省生物医学工程学会", viewBox: "269 129 151 52" },
  { title: "中国科学院上海硅酸盐研究所", viewBox: "448 133 292 45" },
  { title: "西南交通大学", viewBox: "770 128 137 55" },
  { title: "北京科技大学", viewBox: "933 127 243 57" },
  { title: "锦波生物", viewBox: "1199 135 100 42" },
  { title: "嘉思特医疗", viewBox: "574 201 246 49" },
];
</script>
<template>
  <div class="partner-list" aria-label="建设单位标识">
    <template v-if="partners.length">
      <component
        :is="p.url ? 'a' : 'div'"
        v-for="(p, i) in partners"
        :key="i"
        :href="p.url || undefined"
        class="partner-entry"
        :title="p.title"
      >
        <img
          v-if="p.image"
          :src="p.image"
          :alt="p.title"
          loading="lazy"
        /><strong v-else>{{ p.title }}</strong>
      </component>
    </template>
    <div
      v-else
      v-for="(partner, i) in referencePartners"
      :key="partner.title"
      class="partner-entry"
      :title="partner.title"
      :style="{
        '--partner-width': `calc((100% - 120px) * ${Number(partner.viewBox.split(' ')[2]) / 1077})`,
      }"
    >
      <svg
        :viewBox="partner.viewBox"
        role="img"
        :aria-label="partner.title"
        class="partner-reference-logo"
      >
        <defs>
          <clipPath :id="`${clipPrefix}-${i}`">
            <rect
              :x="partner.viewBox.split(' ')[0]"
              :y="partner.viewBox.split(' ')[1]"
              :width="partner.viewBox.split(' ')[2]"
              :height="partner.viewBox.split(' ')[3]"
            />
          </clipPath>
        </defs>
        <image
          :href="reference"
          width="1544"
          height="334"
          :clip-path="`url(#${clipPrefix}-${i})`"
        />
      </svg>
    </div>
  </div>
</template>

<style scoped>
.partner-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 12px 24px;
  padding: 0;
}
.partner-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--partner-width, calc((100% - 120px) / 6));
  min-width: 0;
  min-height: 60px;
  padding: 4px 0;
}
.partner-list img,
.partner-reference-logo {
  display: block;
  width: 100%;
  height: 56px;
  max-width: none;
  object-fit: contain;
}
.partner-list strong {
  font-size: 16px;
  color: #285e95;
  text-align: center;
}

@media (min-width: 701px) and (max-width: 1100px) {
  .partner-entry {
    width: calc((100% - 48px) / 3);
  }
}
@media (max-width: 700px) {
  .partner-entry {
    width: calc((100% - 24px) / 2);
  }
}
</style>
