<script setup lang="ts">
import {
  reactive,
  ref,
  computed,
  onMounted,
  onBeforeUnmount,
  nextTick,
  watch,
} from "vue";
import { useRoute, useRouter, onBeforeRouteLeave } from "vue-router";
import { api, send, upload, notify, type Row } from "../api";
import RichEditor from "../components/RichEditor.vue";
import ArticleContent from "../components/ArticleContent.vue";
import { productCategories, productTags } from "../products";
import { industryRegions, topicsFor, isIndustryCategory } from "../industry";
const route = useRoute(),
  router = useRouter(),
  categories = ref<Row[]>([]),
  busy = ref(false),
  loaded = ref(false),
  snapshot = ref(""),
  preview = ref<Row | null>(null),
  mediaBusy = ref(false),
  bodyBusy = ref(false),
  formElement = ref<HTMLFormElement>(),
  bodyEditor = ref<InstanceType<typeof RichEditor>>();
const uploadingMedia = computed(() => mediaBusy.value || bodyBusy.value);
const form = reactive<Row>({
  title: "",
  subtitle: "",
  categoryId: String(route.query.category || "headlines"),
  source: "",
  summary: "",
  body: "",
  cover: "",
  homeImage: "",
  cooperationImage: "",
  cooperationImageFit: "cover",
  displayDate: new Date().toLocaleDateString("en-CA"),
  documentNumber: "",
  department: "",
  keywords: "",
  domain: "",
  url: "",
  pinned: false,
  recommended: false,
  sort: 0,
  productCategory: "",
  productTag: "",
  industryRegion:
    route.query.region === "international" ? "international" : "domestic",
  industryTopic:
    topicsFor(String(route.query.region || "domestic")).find(
      (t) => t.value === route.query.topic,
    )?.value || topicsFor(String(route.query.region || "domestic"))[0]!.value,
  industryRecent: false,
  industryFeatured: false,
  attachments: [],
  attachmentInfo: [],
  version: 0,
});
const category = computed(() =>
    categories.value.find((c) => c.id === form.categoryId),
  ),
  dirty = computed(
    () => loaded.value && snapshot.value !== JSON.stringify(form),
  );
const isIndustry = computed(() =>
  isIndustryCategory(form.categoryId, categories.value),
);
const availableIndustryTopics = computed(() => topicsFor(form.industryRegion));
watch(
  () => form.industryRegion,
  () => {
    if (
      !availableIndustryTopics.value.some((t) => t.value === form.industryTopic)
    )
      form.industryTopic = availableIndustryTopics.value[0]!.value;
    if (form.industryRegion === "international") {
      form.industryRecent = false;
      form.industryFeatured = false;
    }
  },
  { flush: "sync" },
);
const isCooperation = computed(
  () =>
    form.categoryId === "cooperation" ||
    category.value?.parent_id === "cooperation",
);
const cooperationCategories = computed(() =>
  categories.value.filter((c) => c.parent_id === "cooperation" && c.enabled),
);
const cooperationCategoryId = computed({
  get: () => (form.categoryId === "cooperation" ? "" : form.categoryId),
  set: (value: string) => {
    form.categoryId = value || "cooperation";
  },
});
const orderedCategories = computed(() =>
  categories.value
    .filter((c) => !c.parent_id && c.enabled)
    .flatMap((root) => [
      root,
      ...categories.value.filter((c) => c.parent_id === root.id && c.enabled),
    ]),
);
const cooperationImages = computed(() => {
  const images = new Map<string, string>();
  const add = (url: string, label: string) => {
    if (/^\/api\/media\/[a-f0-9-]{36}$/.test(url) && !images.has(url))
      images.set(url, label);
  };
  add(form.cover || "", "内容封面");
  const document = new DOMParser().parseFromString(
    form.body || "",
    "text/html",
  );
  Array.from(document.querySelectorAll("img[src]")).forEach((image, index) =>
    add(image.getAttribute("src") || "", `正文图片 ${index + 1}`),
  );
  (form.attachmentInfo || [])
    .filter(
      (file: Row) =>
        file.mime?.startsWith("image/") && form.attachments.includes(file.id),
    )
    .forEach((file: Row) => add("/api/media/" + file.id, file.original_name));
  return Array.from(images, ([url, label]) => ({ url, label }));
});
const cooperationPreviewImage = computed(
  () => form.cooperationImage || cooperationImages.value[0]?.url || "",
);
watch(cooperationImages, (images) => {
  if (
    loaded.value &&
    !busy.value &&
    form.cooperationImage &&
    !images.some((image) => image.url === form.cooperationImage)
  ) {
    form.cooperationImage = "";
    notify("原选图片已从文章移除，交流合作首页图片已改为自动选择");
  }
});
async function load() {
  loaded.value = false;
  try {
    categories.value = await api("/api/operator/categories");
    if (route.params.id !== "new")
      Object.assign(
        form,
        await api("/api/operator/contents/" + route.params.id),
      );
    snapshot.value = JSON.stringify(form);
    loaded.value = true;
  } catch (e) {
    notify((e as Error).message, true);
  }
}
onMounted(load);
watch(
  () => route.params.id,
  (id, old) => {
    if (id !== old && !busy.value) load();
  },
);
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value || uploadingMedia.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
window.addEventListener("beforeunload", beforeUnload);
onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
onBeforeRouteLeave(
  () =>
    busy.value ||
    (!dirty.value && !uploadingMedia.value) ||
    confirm("存在尚未保存的修改或正在上传的文件，确认离开？"),
);
async function save(publish = false) {
  if (
    busy.value ||
    uploadingMedia.value ||
    !formElement.value?.reportValidity()
  )
    return false;
  busy.value = true;
  try {
    // Disable every field before starting the request so late edits cannot be
    // replaced by the saved response.
    await nextTick();
    const fresh = route.params.id === "new";
    const saved = await send(
      fresh
        ? "/api/operator/contents"
        : "/api/operator/contents/" + route.params.id,
      form,
      fresh ? "POST" : "PUT",
    );
    Object.assign(form, saved);
    snapshot.value = JSON.stringify(form);
    if (fresh) await router.replace("/operator/edit/" + saved.id);
    if (publish) {
      Object.assign(
        form,
        await send(`/api/operator/contents/${saved.id}/publish`, {
          version: saved.version,
        }),
      );
      snapshot.value = JSON.stringify(form);
    }
    notify(publish ? "内容已发布到对应栏目" : "草稿已保存，公开版本保持不变");
    return true;
  } catch (e) {
    notify((e as Error).message, true);
    return false;
  } finally {
    busy.value = false;
  }
}
async function showPreview() {
  if (!(await save()) || !form.id) return;
  try {
    preview.value = await api("/api/operator/contents/" + form.id);
  } catch (e) {
    notify((e as Error).message, true);
  }
}
const crop = ref<{ url: string; file: File; scale: number } | null>(null);
function chooseCover(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (file) {
    crop.value = { url: URL.createObjectURL(file), file, scale: 1 };
  }
  (e.target as HTMLInputElement).value = "";
}
function closeCrop() {
  if (crop.value) URL.revokeObjectURL(crop.value.url);
  crop.value = null;
}
async function uploadCrop() {
  if (!crop.value) return;
  mediaBusy.value = true;
  try {
    const img = new Image();
    img.src = crop.value.url;
    await img.decode();
    const canvas = document.createElement("canvas");
    canvas.width = 600;
    canvas.height = 400;
    const scale =
      Math.max(600 / img.width, 400 / img.height) * crop.value.scale;
    const ctx = canvas.getContext("2d")!;
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, 600, 400);
    ctx.drawImage(
      img,
      (600 - img.width * scale) / 2,
      (400 - img.height * scale) / 2,
      img.width * scale,
      img.height * scale,
    );
    const blob = await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error("裁剪失败"))),
        "image/jpeg",
        0.9,
      ),
    );
    const f = await upload(
      new File([blob], "cover.jpg", { type: "image/jpeg" }),
    );
    form.cover = f.url;
    closeCrop();
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    mediaBusy.value = false;
  }
}
async function attach(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0];
  if (!f) return;
  mediaBusy.value = true;
  try {
    const media = await upload(f);
    form.attachments.push(media.id);
    form.attachmentInfo.push({
      id: media.id,
      original_name: media.name,
      mime: media.mime,
      size_bytes: f.size,
    });
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    mediaBusy.value = false;
    (e.target as HTMLInputElement).value = "";
  }
}
async function chooseHomeImage(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  if (
    !/\.(png|jpe?g)$/i.test(file.name) ||
    !["image/png", "image/jpeg"].includes(file.type)
  ) {
    notify("请上传 PNG 或 JPG 图片", true);
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    notify("图片不能超过 10 MB", true);
    return;
  }
  mediaBusy.value = true;
  try {
    form.homeImage = (await upload(file)).url;
    notify("首页图片已上传，勾选首页推荐并发布后展示");
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    mediaBusy.value = false;
  }
}
function removeAttachment(id: string) {
  form.attachments = form.attachments.filter((x: string) => x !== id);
  form.attachmentInfo = form.attachmentInfo.filter((x: Row) => x.id !== id);
}
function isImageAttachment(file: Row) {
  return (
    file.mime?.startsWith("image/") ||
    /\.(png|jpe?g)$/i.test(file.original_name)
  );
}
function attachmentInBody(file: Row) {
  const body = new DOMParser().parseFromString(form.body || "", "text/html");
  return Array.from(body.querySelectorAll("img")).some(
    (image) => image.getAttribute("src") === "/api/media/" + file.id,
  );
}
function insertAttachment(file: Row) {
  if (uploadingMedia.value || busy.value) return;
  bodyEditor.value?.chooseImagePosition(
    "/api/media/" + file.id,
    file.original_name,
  );
}
</script>
<template>
  <div class="admin-breadcrumb">
    <RouterLink to="/operator">内容管理</RouterLink> ›
    {{ route.params.id === "new" ? "添加" : "编辑" }}内容
  </div>
  <section class="admin-panel editor-panel publishing-workspace">
    <h2>
      {{ route.params.id === "new" ? "发布一篇新内容" : "编辑内容" }}
      <span
        >{{ category?.name }} ·
        {{ form.status === "PUBLISHED" ? "已发布" : "草稿" }}</span
      >
    </h2>
    <form v-if="loaded" ref="formElement" @submit.prevent="save(false)">
      <fieldset class="publishing-fields" :disabled="busy" :aria-busy="busy">
        <div class="publishing-grid">
          <div class="writing-main">
            <section class="editor-section">
              <div class="section-intro">
                <h3>基本信息</h3>
                <p>写好标题、选好栏目，即可开始编辑正文。</p>
              </div>
              <div class="base-fields">
                <label class="full">
                  <span>内容标题 <b>*</b></span>
                  <input
                    v-model="form.title"
                    required
                    maxlength="200"
                    class="title-input"
                    placeholder="输入完整、清晰的内容标题"
                  />
                </label>
                <label>
                  <span>所属栏目 <b>*</b></span>
                  <select v-model="form.categoryId" required>
                    <option
                      v-for="c in orderedCategories"
                      :key="c.id"
                      :value="c.id"
                    >
                      {{ c.parent_id ? "　└ " : "" }}{{ c.name
                      }}{{ c.id === "cooperation" ? "（选择下方分类）" : "" }}
                    </option>
                  </select>
                </label>
                <label>
                  <span>显示日期 <b>*</b></span>
                  <input type="date" v-model="form.displayDate" required />
                </label>
                <label>
                  信息来源
                  <input v-model="form.source" placeholder="选填，如发布单位" />
                </label>
                <template v-if="isIndustry">
                  <label
                    >动态区域
                    <select v-model="form.industryRegion" required>
                      <option
                        v-for="option in industryRegions"
                        :key="option.value"
                        :value="option.value"
                      >
                        {{ option.label }}
                      </option>
                    </select></label
                  >
                  <label
                    >内容分类
                    <select v-model="form.industryTopic" required>
                      <option
                        v-for="option in availableIndustryTopics"
                        :key="option.value"
                        :value="option.value"
                      >
                        {{ option.label }}
                      </option>
                    </select></label
                  >
                  <label class="full"
                    >原文链接（选填）<input
                      v-model="form.url"
                      placeholder="https://…，在文章详情中显示“查看原文”"
                  /></label>
                </template>
                <label v-if="category?.type === 'PRODUCT'">
                  产品分类
                  <select v-model="form.productCategory">
                    <option value="">未分类</option>
                    <option
                      v-for="name in productCategories"
                      :key="name"
                      :value="name"
                    >
                      {{ name }}
                    </option>
                  </select>
                </label>
                <label v-if="category?.type === 'PRODUCT'">
                  产品标签
                  <select v-model="form.productTag">
                    <option value="">无标签</option>
                    <option v-for="tag in productTags" :key="tag" :value="tag">
                      {{ tag }}
                    </option>
                  </select>
                </label>
                <label
                  v-if="
                    ['RESOURCE', 'PRODUCT', 'LINK'].includes(category?.type)
                  "
                  class="full"
                >
                  资源 / 产品访问地址
                  <input
                    v-model="form.url"
                    placeholder="https://… 或本站路径"
                  />
                </label>
              </div>
            </section>

            <section
              v-if="isCooperation"
              class="editor-section cooperation-category-field"
            >
              <label>
                <span>交流合作分类 <b>*</b></span>
                <select v-model="cooperationCategoryId" required>
                  <option disabled value="">请选择文章所属分类</option>
                  <option
                    v-for="child in cooperationCategories"
                    :key="child.id"
                    :value="child.id"
                  >
                    {{ child.name }}
                  </option>
                </select>
                <span class="field-hint"
                  >文章发布到此分类列表；首页分类图片和“更多”均进入该列表。</span
                >
              </label>
            </section>

            <section class="editor-section body-section">
              <div class="section-intro">
                <h3>正文内容</h3>
                <p>
                  点击“插入正文图片”，可选择光标处或指定段落之后。选中已有图片，可再次调整位置。
                </p>
              </div>
              <RichEditor
                ref="bodyEditor"
                v-model="form.body"
                :disabled="busy || mediaBusy"
                @busy="bodyBusy = $event"
              />
            </section>

            <section class="editor-section file-section">
              <div class="section-intro file-heading">
                <div>
                  <h3>附件 <span class="optional-label">选填</span></h3>
                  <p>
                    图片可选择插入正文的位置，未插入的图片在文末展示；PDF、TXT
                    提供下载。
                  </p>
                </div>
                <label
                  class="btn attachment-upload"
                  :class="{ disabled: busy || uploadingMedia }"
                >
                  ＋ 添加附件
                  <input
                    type="file"
                    accept=".pdf,.txt,.png,.jpg,.jpeg"
                    hidden
                    @change="attach"
                    :disabled="busy || uploadingMedia"
                  />
                </label>
              </div>
              <div v-if="!form.attachmentInfo.length" class="attachment-empty">
                支持 PDF、TXT、PNG、JPG，单个文件最大 10 MB。
              </div>
              <div
                v-for="f in form.attachmentInfo"
                :key="f.id"
                class="attachment-row"
              >
                <a
                  v-if="isImageAttachment(f)"
                  :href="'/api/media/' + f.id"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="attachment-thumbnail"
                >
                  <img :src="'/api/media/' + f.id" :alt="f.original_name" />
                </a>
                <span v-else class="attachment-file-icon" aria-hidden="true"
                  >文档</span
                >
                <div class="attachment-description">
                  <a
                    :href="'/api/media/' + f.id"
                    target="_blank"
                    rel="noopener noreferrer"
                    >{{ f.original_name }}</a
                  >
                  <small
                    >{{
                      isImageAttachment(f)
                        ? attachmentInBody(f)
                          ? "已插入正文"
                          : "将在文章末尾展示"
                        : "附件下载"
                    }}<template v-if="f.size_bytes">
                      · {{ Math.ceil(f.size_bytes / 1024) }} KB</template
                    ></small
                  >
                </div>
                <div class="attachment-row-actions">
                  <button
                    v-if="isImageAttachment(f)"
                    type="button"
                    class="text-button"
                    @click="insertAttachment(f)"
                    :disabled="busy || uploadingMedia"
                  >
                    {{ attachmentInBody(f) ? "调整图片位置" : "选择插入位置" }}
                  </button>
                  <button
                    type="button"
                    class="text-button danger-text"
                    @click="removeAttachment(f.id)"
                    :disabled="busy || uploadingMedia"
                  >
                    移除附件
                  </button>
                </div>
              </div>
            </section>

            <details class="editor-section advanced-settings">
              <summary>
                <span>更多设置</span><small>列表简介、副标题及发布选项</small>
              </summary>
              <div class="form-grid advanced-fields">
                <label class="full">
                  列表简介（选填）
                  <textarea
                    v-model="form.summary"
                    rows="3"
                    maxlength="2000"
                    placeholder="不填会自动摘取正文；填写后用于列表和首页卡片介绍"
                  ></textarea>
                  <span class="field-hint">正文页不重复显示这段简介。</span>
                </label>
                <label class="full"
                  >副标题<input
                    v-model="form.subtitle"
                    placeholder="选填，作为标题的补充说明"
                /></label>
                <label
                  >文号<input
                    v-model="form.documentNumber"
                    placeholder="通知公告可填写"
                /></label>
                <label
                  >提供单位<input v-model="form.department" placeholder="选填"
                /></label>
                <label class="full"
                  >关键词<input
                    v-model="form.keywords"
                    placeholder="选填，多个关键词用逗号分隔"
                /></label>
                <label v-if="isIndustry"
                  >材料领域（选填）<input
                    v-model="form.domain"
                    placeholder="如：生物医用材料"
                /></label>
              </div>
            </details>
          </div>

          <aside class="publishing-aside">
            <section
              v-if="isCooperation"
              class="publish-card cooperation-image-panel"
            >
              <h3>交流合作首页图片</h3>
              <p>
                从本文的正文、封面或图片附件中选一张，作为所属分类的首页展示图。
              </p>
              <div
                v-if="cooperationPreviewImage"
                class="cooperation-cover-preview"
              >
                <img
                  :src="cooperationPreviewImage"
                  alt="交流合作首页图片预览"
                  :style="{ objectFit: form.cooperationImageFit }"
                />
              </div>
              <div
                v-if="cooperationImages.length"
                class="cooperation-image-options"
                role="radiogroup"
                aria-label="选择交流合作首页图片"
              >
                <label
                  class="cooperation-auto-image"
                  :class="{ selected: !form.cooperationImage }"
                >
                  <input
                    v-model="form.cooperationImage"
                    type="radio"
                    name="cooperationImage"
                    value=""
                  />
                  自动使用文章第一张图片
                </label>
                <label
                  v-for="image in cooperationImages"
                  :key="image.url"
                  class="cooperation-image-option"
                  :class="{ selected: form.cooperationImage === image.url }"
                >
                  <input
                    v-model="form.cooperationImage"
                    type="radio"
                    name="cooperationImage"
                    :value="image.url"
                    :aria-label="'使用' + image.label"
                  />
                  <img :src="image.url" alt="" />
                  <span>{{ image.label }}</span>
                </label>
              </div>
              <p v-else class="cooperation-no-image">
                文章中还没有图片。请先插入正文图片、添加图片附件或上传内容封面。
              </p>
              <label
                v-if="cooperationImages.length"
                class="cooperation-fit-field"
              >
                图片展示方式
                <select v-model="form.cooperationImageFit">
                  <option value="cover">铺满区域（适合横向照片）</option>
                  <option value="contain">完整显示（适合海报、期刊）</option>
                </select>
              </label>
              <p class="field-hint">
                发布后生效，无需勾选站点首页推荐。同一分类优先采用手动选图的文章，再按置顶、展示优先级、显示日期选取。撤回后自动采用下一篇可用文章的图片。
              </p>
            </section>
            <section v-if="isIndustry" class="publish-card">
              <h3>领域动态展示</h3>
              <p>
                发布到{{
                  form.industryRegion === "international"
                    ? "国际动态"
                    : "国内动态"
                }}
                /
                {{
                  availableIndustryTopics.find(
                    (t) => t.value === form.industryTopic,
                  )?.label
                }}。
              </p>
              <template v-if="form.industryRegion === 'domestic'">
                <label class="check-label home-recommend"
                  ><input
                    type="checkbox"
                    v-model="form.industryRecent"
                  />加入近期要闻</label
                >
                <label class="check-label home-recommend"
                  ><input
                    type="checkbox"
                    v-model="form.industryFeatured"
                  />加入图片推荐</label
                >
                <p>
                  近期要闻显示在国内动态右侧。图片推荐显示在左侧轮播，需上传推荐图片或内容封面。
                </p>
              </template>
              <p v-else>
                国际动态以列表展示标题、来源和日期，点击“查看”进入文章详情。
              </p>
              <p>
                列表和推荐位置均按置顶、展示优先级、显示日期排序；发布更新后生效。
              </p>
            </section>
            <section class="publish-card priority-panel">
              <h3>展示优先级</h3>
              <label
                >优先级数值
                <input
                  type="number"
                  v-model.number="form.sort"
                  min="-10000"
                  max="10000"
                  step="1"
                  required
                  aria-describedby="priority-help"
                />
              </label>
              <p id="priority-help" class="field-hint">
                数值越大越靠前，默认
                0。置顶内容优先；同为置顶或普通内容时，按优先级从高到低、显示日期从新到旧排列。
              </p>
              <label class="check-label"
                ><input type="checkbox" v-model="form.pinned" />置顶展示</label
              >
              <p class="field-hint">
                适用于首页推荐与栏目默认排序。点击“立即发布”或“发布更新”后生效，仅保存草稿不会改变网页顺序。
              </p>
            </section>
            <section
              v-if="!isIndustry || form.industryRegion === 'domestic'"
              class="publish-card home-image-panel"
            >
              <h3>
                {{ isIndustry ? "推荐图片" : "首页图片" }}
                <span class="optional-label">选填</span>
              </h3>
              <label
                class="cover-upload home-image-upload"
                :class="{ disabled: busy || uploadingMedia }"
              >
                <img
                  v-if="form.homeImage"
                  :src="form.homeImage"
                  :alt="isIndustry ? '推荐图片预览' : '首页图片预览'"
                />
                <template v-else
                  ><span>＋</span
                  ><strong>{{
                    isIndustry ? "选择推荐图片" : "选择首页图片"
                  }}</strong
                  ><small>PNG / JPG · 建议横向图片</small></template
                >
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  :aria-label="isIndustry ? '上传推荐图片' : '上传首页图片'"
                  hidden
                  @change="chooseHomeImage"
                  :disabled="busy || uploadingMedia"
                />
              </label>
              <button
                v-if="form.homeImage"
                type="button"
                class="text-button"
                @click="form.homeImage = ''"
                :disabled="busy || uploadingMedia"
              >
                {{ isIndustry ? "移除推荐图片" : "移除首页图片" }}
              </button>
              <label v-if="!isIndustry" class="check-label home-recommend"
                ><input
                  type="checkbox"
                  v-model="form.recommended"
                />推荐到首页对应区域</label
              >
              <p v-if="isIndustry">
                勾选“加入图片推荐”后显示于国内动态左侧轮播，点击可进入文章。未设置推荐图片时使用内容封面。
              </p>
              <p v-else>
                用于首页新闻大图或产品卡片，点击可进入对应文章。发布后生效；未设置时沿用内容封面。
              </p>
            </section>
            <section class="publish-card cover-panel">
              <h3>内容封面 <span class="optional-label">选填</span></h3>
              <label
                class="cover-upload"
                :class="{ disabled: busy || uploadingMedia }"
              >
                <img v-if="form.cover" :src="form.cover" alt="封面预览" />
                <template v-else
                  ><span>＋</span><strong>选择封面图片</strong
                  ><small>PNG / JPG · 3:2</small></template
                >
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  hidden
                  @change="chooseCover"
                  :disabled="busy || uploadingMedia"
                />
              </label>
              <button
                v-if="form.cover"
                type="button"
                class="text-button"
                @click="form.cover = ''"
                :disabled="busy || uploadingMedia"
              >
                移除封面
              </button>
              <p>用于列表卡片和文章开头。正文中的配图，请在正文工具栏插入。</p>
            </section>
            <section class="publish-card publishing-note">
              <h3>发布状态</h3>
              <span
                class="publish-state"
                :class="{ published: form.status === 'PUBLISHED' }"
                >{{ form.status === "PUBLISHED" ? "已发布" : "草稿" }}</span
              >
              <p v-if="form.hasChanges">
                当前有尚未发布的修改，发布更新后读者才能看到。
              </p>
              <p v-else>可先保存并预览阅读效果，确认后再发布。</p>
              <p class="draft-note">保存草稿不会改变已公开的版本。</p>
            </section>
          </aside>
        </div>
        <div class="editor-actions">
          <span role="status">{{
            uploadingMedia
              ? "正在上传图片或附件…"
              : dirty
                ? "有未保存修改"
                : "修改已保存"
          }}</span>
          <RouterLink class="btn" to="/operator">返回列表</RouterLink>
          <button
            type="button"
            class="btn"
            @click="showPreview"
            :disabled="busy || uploadingMedia"
          >
            保存并预览
          </button>
          <button class="btn outline" :disabled="busy || uploadingMedia">
            保存草稿
          </button>
          <button
            type="button"
            class="btn primary"
            @click="save(true)"
            :disabled="busy || uploadingMedia"
          >
            {{
              busy
                ? "正在保存…"
                : form.status === "PUBLISHED"
                  ? "发布更新"
                  : "立即发布"
            }}
          </button>
        </div>
      </fieldset>
    </form>
    <div v-else class="empty">正在加载编辑器…</div>
  </section>
  <div v-if="preview" class="modal-backdrop" @click.self="preview = null">
    <section
      class="modal preview-modal"
      role="dialog"
      aria-modal="true"
      aria-label="草稿预览"
    >
      <div class="modal-head">
        <strong>草稿预览 · 仅操作员可见</strong
        ><button type="button" @click="preview = null" aria-label="关闭预览">
          ×
        </button>
      </div>
      <ArticleContent :item="preview" />
    </section>
  </div>
  <div v-if="crop" class="modal-backdrop">
    <section
      class="modal crop-modal"
      role="dialog"
      aria-modal="true"
      aria-label="封面裁剪"
    >
      <div class="modal-head">
        <strong>封面裁剪 · 3:2</strong
        ><button
          type="button"
          @click="closeCrop"
          :disabled="mediaBusy"
          aria-label="关闭裁剪"
        >
          ×
        </button>
      </div>
      <div class="crop-window">
        <img
          :src="crop.url"
          :style="{ transform: `scale(${crop.scale})` }"
          alt="裁剪预览"
        />
      </div>
      <label
        >缩放<input
          type="range"
          min="1"
          max="3"
          step=".05"
          v-model.number="crop.scale"
          :disabled="mediaBusy"
      /></label>
      <button
        type="button"
        class="btn primary"
        @click="uploadCrop"
        :disabled="mediaBusy"
      >
        {{ mediaBusy ? "正在上传…" : "确认裁剪并上传" }}
      </button>
    </section>
  </div>
</template>

<style scoped>
.cooperation-category-field {
  padding: 16px;
  background: #f3f7fd;
  border: 1px solid #dce7f6;
  border-radius: 6px;
}
.cooperation-cover-preview {
  aspect-ratio: 1.82;
  overflow: hidden;
  border-radius: 8px;
  margin: 16px 0;
  background: #e9f3ff;
}
.cooperation-cover-preview img {
  display: block;
  width: 100%;
  height: 100%;
}
.cooperation-image-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
  padding: 3px;
}
.cooperation-image-option {
  position: relative;
  min-width: 0;
  padding: 6px;
  border: 2px solid #e1e8f0;
  border-radius: 6px;
  cursor: pointer;
}
.cooperation-image-option.selected,
.cooperation-auto-image.selected {
  border-color: #2867c4;
  background: #f0f6ff;
}
.cooperation-image-option input {
  position: absolute;
  top: 9px;
  left: 9px;
}
.cooperation-image-options input[type="radio"] {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: #2563c9;
  flex-shrink: 0;
}
.cooperation-image-option img {
  width: 100%;
  height: 72px;
  object-fit: contain;
  background: #f0f3f8;
}
.cooperation-image-option span {
  font-size: 12px;
  overflow-wrap: anywhere;
  line-height: 1.5;
}
.cooperation-auto-image {
  display: flex;
  flex-direction: row;
  align-items: center;
  grid-column: 1 / -1;
  padding: 10px 8px;
  border: 2px solid #e1e8f0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.cooperation-fit-field {
  margin: 16px 0;
}
.cooperation-no-image {
  padding: 14px;
  background: #f1f5fa;
  border-radius: 6px;
}
.cooperation-image-options label:focus-within {
  outline: 2px solid #2867c4;
  outline-offset: 1px;
}
.publishing-workspace {
  max-width: 1450px;
  margin: 0 auto;
  border-top: 0;
  border-radius: 8px;
  overflow: clip;
}
.publishing-workspace > h2 {
  padding: 22px 28px;
  font-size: 19px;
  font-weight: 600;
}
.publishing-fields {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}
.publishing-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
  gap: 32px;
  padding: 28px;
  align-items: start;
}
.writing-main {
  min-width: 0;
}
.editor-section {
  margin-bottom: 30px;
}
.section-intro {
  margin-bottom: 18px;
}
.section-intro h3,
.publish-card h3 {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #304d63;
}
.section-intro p,
.publish-card p,
.field-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: #778998;
}
.base-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px 16px;
}
.title-input {
  padding: 13px 14px;
  font-size: 18px;
}
.body-section {
  margin-top: 30px;
}
.body-section :deep(.rich-editor) {
  margin: 0;
  border-radius: 6px;
  overflow: hidden;
}
.body-section :deep(.rich-body) {
  min-height: 420px;
  padding: 24px;
}
.file-section {
  border: 1px solid #e3eaf1;
  border-radius: 6px;
  padding: 20px;
}
.file-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.attachment-upload {
  flex-direction: row;
  flex-shrink: 0;
}
.optional-label {
  margin-left: 6px;
  color: #95a3ae;
  font-size: 11px;
  font-weight: 400;
}
.attachment-empty {
  padding: 12px 0 0;
  color: #8999a7;
  font-size: 12px;
  border-top: 1px dashed #e1e8ef;
}
.attachment-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid #edf1f5;
}
.attachment-thumbnail,
.attachment-file-icon {
  width: 64px;
  height: 52px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;
  background: #eff4f9;
}
.attachment-thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.attachment-file-icon {
  display: grid;
  place-items: center;
  color: #7795ad;
  font-size: 12px;
}
.attachment-description {
  min-width: 0;
  flex: 1;
}
.attachment-description a {
  color: #347cae;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.attachment-description small {
  display: block;
  margin-top: 5px;
  font-size: 11px;
  color: #8a9cab;
}
.attachment-row-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}
.attachment-row-actions button {
  white-space: nowrap;
}
.advanced-settings {
  border: 1px solid #e3eaf1;
  border-radius: 6px;
  margin-bottom: 0;
}
.advanced-settings summary {
  padding: 18px 20px;
  cursor: pointer;
  color: #3d5a70;
  font-size: 14px;
}
.advanced-settings summary small {
  margin-left: 14px;
  color: #8d9ca9;
  font-size: 12px;
}
.advanced-settings[open] summary {
  border-bottom: 1px solid #edf1f5;
}
.advanced-fields {
  padding: 22px 20px;
}
.publishing-aside {
  display: grid;
  gap: 20px;
}
.home-image-upload img {
  object-fit: contain;
}
.home-recommend {
  margin-top: 16px;
}
.publish-card {
  border: 1px solid #e3eaf1;
  border-radius: 6px;
  padding: 20px;
  background: #fbfcfe;
}
.publish-card h3 {
  margin-bottom: 16px;
}
.publish-card p {
  margin-top: 14px;
}
.cover-upload {
  height: 155px;
  border-radius: 5px;
}
.cover-upload > span {
  font-size: 27px;
}
.cover-upload small {
  font-size: 11px;
}
.publish-state {
  display: inline-block;
  padding: 5px 10px;
  font-size: 12px;
  color: #9e7c3a;
  background: #fff5e3;
  border-radius: 4px;
}
.publish-state.published {
  color: #28846d;
  background: #eaf7f0;
}
.draft-note {
  border-top: 1px solid #e6edf3;
  padding-top: 12px;
}
.editor-actions {
  margin-top: 0;
  padding: 18px 28px;
}
.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
@media (max-width: 1100px) {
  .publishing-grid {
    grid-template-columns: minmax(0, 1fr) 230px;
    gap: 24px;
    padding: 24px;
  }
  .base-fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .publish-card {
    padding: 16px;
  }
  .file-heading {
    flex-wrap: wrap;
  }
}
@media (max-width: 800px) {
  .publishing-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .publishing-aside {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .cover-panel {
    max-width: none;
  }
}
@media (max-width: 700px) {
  .publishing-workspace > h2 {
    padding: 18px;
    font-size: 17px;
    gap: 12px;
    align-items: flex-start;
  }
  .publishing-workspace > h2 > span {
    text-align: right;
    font-size: 11px;
  }
  .publishing-grid {
    padding: 18px;
    gap: 20px;
  }
  .base-fields {
    grid-template-columns: minmax(0, 1fr);
  }
  .base-fields .full {
    grid-column: 1 / -1;
  }
  .title-input {
    font-size: 16px;
  }
  .body-section :deep(.rich-body) {
    padding: 18px;
    min-height: 360px;
  }
  .file-section {
    padding: 16px;
  }
  .attachment-row {
    flex-wrap: wrap;
  }
  .attachment-row-actions {
    width: 100%;
    padding-left: 76px;
    justify-content: flex-start;
  }
  .advanced-settings summary small {
    display: block;
    margin: 7px 0 0 18px;
    line-height: 1.5;
  }
  .advanced-fields {
    padding: 18px;
  }
  .publishing-aside {
    grid-template-columns: minmax(0, 1fr);
  }
  .editor-actions {
    padding: 14px 18px;
  }
}
</style>
