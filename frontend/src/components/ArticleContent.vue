<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { Row } from "../api";
import { industryLabel } from "../industry";

const props = defineProps<{ item: Row }>();
const failedImages = ref(new Set<string>());
const bodyImages = computed(() => {
  const document = new DOMParser().parseFromString(
    props.item.body || "",
    "text/html",
  );
  return new Set(
    Array.from(document.querySelectorAll("img[src]")).map((image) =>
      image.getAttribute("src"),
    ),
  );
});
const cover = computed(() =>
  props.item.cover && !bodyImages.value.has(props.item.cover)
    ? props.item.cover
    : "",
);
const imageAttachments = computed(() =>
  (props.item.attachmentInfo || []).filter(
    (file: Row) =>
      file.mime?.startsWith("image/") &&
      "/api/media/" + file.id !== props.item.cover &&
      !bodyImages.value.has("/api/media/" + file.id),
  ),
);
const documents = computed(() =>
  (props.item.attachmentInfo || []).filter(
    (file: Row) => !file.mime?.startsWith("image/"),
  ),
);
watch(
  () => props.item,
  () => (failedImages.value = new Set()),
);
function imageFailed(url: string) {
  failedImages.value.add(url);
}
function fileSize(bytes: number) {
  if (!bytes) return "";
  return bytes >= 1024 * 1024
    ? (bytes / 1024 / 1024).toFixed(1) + " MB"
    : Math.max(1, Math.ceil(bytes / 1024)) + " KB";
}
</script>

<template>
  <article class="article reading-article">
    <header class="article-heading">
      <span class="article-kicker">{{
        item.industryContent ? industryLabel(item) : item.categoryName
      }}</span>
      <h1>{{ item.title }}</h1>
      <p v-if="item.subtitle" class="article-subtitle">{{ item.subtitle }}</p>
      <div class="reading-meta">
        <time :datetime="item.displayDate">{{ item.displayDate }}</time>
        <span v-if="item.source">来源：{{ item.source }}</span>
        <span v-if="item.department">提供单位：{{ item.department }}</span>
        <span v-if="item.documentNumber">{{ item.documentNumber }}</span>
      </div>
    </header>
    <figure v-if="cover" class="reading-cover">
      <a
        :href="cover"
        target="_blank"
        rel="noopener noreferrer"
        :aria-label="'查看原图：' + item.title"
      >
        <img
          v-if="!failedImages.has(cover)"
          :src="cover"
          :alt="item.title"
          @error="imageFailed(cover)"
        />
        <span v-else class="image-unavailable"
          >图片暂时无法显示，点击查看原图 ↗</span
        >
      </a>
    </figure>
    <div class="prose reading-body" v-html="item.body"></div>
    <section
      v-if="imageAttachments.length"
      class="article-images"
      aria-label="相关图片"
    >
      <h2 class="reading-section-heading">
        相关图片 <span>{{ imageAttachments.length }}</span>
      </h2>
      <figure
        v-for="file in imageAttachments"
        :key="file.id"
        class="attachment-image"
      >
        <a
          :href="'/api/media/' + file.id"
          target="_blank"
          rel="noopener noreferrer"
          :aria-label="'查看原图：' + file.original_name"
        >
          <img
            v-if="!failedImages.has('/api/media/' + file.id)"
            :src="'/api/media/' + file.id"
            :alt="file.original_name"
            loading="lazy"
            @error="imageFailed('/api/media/' + file.id)"
          />
          <span v-else class="image-unavailable"
            >图片暂时无法显示，点击查看原图 ↗</span
          >
        </a>
        <figcaption>
          <span>{{ file.original_name }}</span
          ><a
            :href="'/api/media/' + file.id"
            target="_blank"
            rel="noopener noreferrer"
            >查看原图 ↗</a
          >
        </figcaption>
      </figure>
    </section>
    <a
      v-if="item.url"
      class="btn primary resource-access"
      :href="item.url"
      target="_blank"
      rel="noopener noreferrer"
      >{{
        item.industryContent
          ? "查看原文"
          : item.type === "LINK"
            ? "访问相关网站"
            : "访问资源 / 产品"
      }}
      ↗</a
    >
    <section
      v-if="documents.length"
      class="reading-downloads"
      aria-label="附件下载"
    >
      <h2 class="reading-section-heading">
        附件下载 <span>{{ documents.length }}</span>
      </h2>
      <a
        v-for="file in documents"
        :key="file.id"
        :href="'/api/media/' + file.id"
        class="download-row"
        :download="file.original_name"
      >
        <span class="file-badge">{{
          file.mime === "application/pdf" ? "PDF" : "TXT"
        }}</span>
        <span class="download-name"
          >{{ file.original_name
          }}<small>{{ fileSize(file.size_bytes) }}</small></span
        >
        <span class="download-action">下载 ↓</span>
      </a>
    </section>
  </article>
</template>

<style scoped>
.reading-article {
  width: 100%;
  min-width: 0;
}
.article-heading {
  padding-bottom: 26px;
  margin-bottom: 32px;
  border-bottom: 1px solid var(--line);
}
.article-kicker {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  margin-bottom: 12px;
}
.reading-article h1 {
  margin: 0;
  font-size: clamp(25px, 2.5vw, 36px);
  line-height: 1.5;
  text-align: left;
  letter-spacing: 0.01em;
  overflow-wrap: anywhere;
  color: #173650;
}
.article-subtitle {
  margin: 16px 0 0;
  color: #63788a;
  font-size: 19px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.reading-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 20px;
  margin-top: 22px;
  color: #758493;
  font-size: 13px;
  line-height: 1.6;
}
.reading-meta time {
  color: inherit;
}
.reading-cover {
  margin: 0 0 32px;
}
.reading-cover a,
.attachment-image > a {
  display: block;
}
.reading-cover img,
.attachment-image img {
  display: block;
  width: auto;
  max-width: 100%;
  height: auto;
  margin: auto;
  border-radius: 6px;
}
.reading-body {
  font-size: 17px;
  line-height: 2;
  color: #34495a;
}
.reading-body :deep(p) {
  margin: 0 0 1.4em;
}
.reading-body :deep(h2) {
  margin: 36px 0 16px;
  font-size: 24px;
  line-height: 1.5;
  color: #203f57;
}
.reading-body :deep(h3) {
  margin: 28px 0 12px;
  font-size: 20px;
  line-height: 1.6;
}
.reading-body :deep(img) {
  display: block;
  margin: 26px auto;
  max-width: 100%;
  height: auto;
  border-radius: 6px;
}
.reading-body :deep(ul),
.reading-body :deep(ol) {
  padding-left: 1.6em;
}
.reading-body :deep(table) {
  display: block;
  overflow-x: auto;
}
.reading-body :deep(pre) {
  overflow-x: auto;
  padding: 16px;
  background: #f4f7fa;
  border-radius: 5px;
  font-size: 14px;
}
.article-images,
.reading-downloads {
  margin-top: 40px;
  padding-top: 28px;
  border-top: 1px solid var(--line);
}
.reading-section-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 600;
  margin: 0 0 22px;
  color: #284b67;
}
.reading-section-heading span {
  font-size: 12px;
  color: #7a8a98;
  font-weight: 400;
}
.attachment-image {
  margin: 0 0 28px;
}
.attachment-image figcaption {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.8;
  color: #7b8c9a;
}
.attachment-image figcaption > span {
  min-width: 0;
  overflow-wrap: anywhere;
}
.attachment-image figcaption > a {
  color: var(--accent);
  flex-shrink: 0;
}
.image-unavailable {
  display: grid;
  place-items: center;
  min-height: 160px;
  padding: 24px;
  background: #f3f7fa;
  color: #687e90;
  border: 1px dashed #d3e0e9;
  font-size: 13px;
}
.download-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  margin-top: 10px;
  border: 1px solid #e2eaf0;
  border-radius: 6px;
  background: #fafcfe;
}
.download-row:hover {
  border-color: #9bbdd7;
  background: #f3f8fc;
}
.file-badge {
  display: grid;
  place-items: center;
  width: 42px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 4px;
  background: #e9f1f8;
  color: #3973a2;
  font-size: 11px;
  font-weight: 700;
}
.download-name {
  min-width: 0;
  font-size: 14px;
  color: #36566e;
  overflow-wrap: anywhere;
}
.download-name small {
  display: block;
  margin-top: 6px;
  color: #8a98a5;
  font-size: 11px;
}
.download-action {
  margin-left: auto;
  flex-shrink: 0;
  color: #347aad;
  font-size: 12px;
}
.resource-access {
  margin-top: 24px;
}
@media (max-width: 700px) {
  .article-heading {
    padding-bottom: 22px;
    margin-bottom: 24px;
  }
  .article-subtitle {
    font-size: 17px;
  }
  .reading-meta {
    font-size: 12px;
    gap: 8px 14px;
    margin-top: 18px;
  }
  .reading-body {
    font-size: 16px;
    line-height: 1.95;
  }
  .reading-body :deep(h2) {
    font-size: 21px;
  }
  .attachment-image figcaption {
    flex-wrap: wrap;
    gap: 4px 14px;
  }
  .download-row {
    padding: 14px;
    gap: 12px;
  }
  .download-name {
    font-size: 13px;
  }
}
</style>
