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
  displayDate: new Date().toLocaleDateString("en-CA"),
  documentNumber: "",
  department: "",
  keywords: "",
  domain: "",
  url: "",
  pinned: false,
  recommended: false,
  sort: 0,
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
                      v-for="c in categories.filter((c) => c.enabled)"
                      :key="c.id"
                      :value="c.id"
                    >
                      {{ c.parent_id ? "　└ " : "" }}{{ c.name }}
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
                <label v-if="form.categoryId === 'industry'"
                  >领域分类<input
                    v-model="form.domain"
                    placeholder="如：生物医用材料"
                /></label>
                <label
                  >排序优先级<input
                    type="number"
                    v-model.number="form.sort"
                    min="-10000"
                    max="10000"
                /></label>
                <label class="check-label"
                  ><input
                    type="checkbox"
                    v-model="form.pinned"
                  />在栏目中置顶</label
                >
                <label class="check-label"
                  ><input
                    type="checkbox"
                    v-model="form.recommended"
                  />推荐到首页对应区域</label
                >
              </div>
            </details>
          </div>

          <aside class="publishing-aside">
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
