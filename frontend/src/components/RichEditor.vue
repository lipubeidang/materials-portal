<script setup lang="ts">
import { watch, onBeforeUnmount, ref, computed, nextTick } from "vue";
import { NodeSelection } from "@tiptap/pm/state";
import { useEditor, EditorContent } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import { upload, notify } from "../api";
const props = withDefaults(
  defineProps<{ modelValue: string; disabled?: boolean }>(),
  {
    disabled: false,
  },
);
const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "busy", value: boolean): void;
}>();
const fileInput = ref<HTMLInputElement>();
const uploading = ref(false);
const positionDialog = ref<HTMLDialogElement>();
const placement = ref<{
  src: string;
  alt: string;
  moving: boolean;
  options: { position: number; label: string }[];
} | null>(null);
const chosenPosition = ref(0);
const selectedImage = ref<{ src: string; alt: string } | null>(null);
const locked = computed(
  () => props.disabled || uploading.value || !!placement.value,
);
let uploadPosition = 0;
let disposed = false;
const editor = useEditor({
  extensions: [StarterKit, Image],
  content: props.modelValue,
  editable: !props.disabled,
  onUpdate: ({ editor }) => emit("update:modelValue", editor.getHTML()),
  onSelectionUpdate: ({ editor }) => {
    const selection = editor.state.selection;
    selectedImage.value =
      selection instanceof NodeSelection && selection.node.type.name === "image"
        ? { src: selection.node.attrs.src, alt: selection.node.attrs.alt || "" }
        : null;
  },
  editorProps: {
    attributes: {
      class: "rich-body",
      role: "textbox",
      "aria-label": "内容正文",
      "aria-multiline": "true",
    },
  },
});
watch(
  () => props.modelValue,
  (value) => {
    if (editor.value && editor.value.getHTML() !== value)
      editor.value.commands.setContent(value, { emitUpdate: false });
  },
);
// Locking the editor is a UI change, not a content edit.
watch(locked, (value) => editor.value?.setEditable(!value, false));
onBeforeUnmount(() => {
  disposed = true;
  editor.value?.destroy();
});
async function chooseImagePosition(src: string, alt = "", cursor?: number) {
  if (!editor.value || props.disabled || disposed) return false;
  let moving = false;
  editor.value.state.doc.descendants((node) => {
    if (node.type.name === "image" && node.attrs.src === src) moving = true;
  });
  const position = cursor ?? editor.value.state.selection.to;
  const options = [
    { position, label: "当前光标处" },
    { position: 0, label: "文章开头" },
  ];
  let paragraph = 0;
  editor.value.state.doc.forEach((node, offset) => {
    if (node.isTextblock) paragraph++;
    if (node.type.name === "image" && node.attrs.src === src) return;
    const text =
      node.textContent.trim() ||
      (node.type.name === "image"
        ? "图片：" + (node.attrs.alt || "正文配图")
        : "空行");
    options.push({
      position: offset + node.nodeSize,
      label: `${node.isTextblock ? `第 ${paragraph} 段之后` : node.type.name === "image" ? "图片之后" : "这段内容之后"} · ${text.slice(0, 70)}${text.length > 70 ? "…" : ""}`,
    });
  });
  chosenPosition.value = 0;
  placement.value = { src, alt, moving, options };
  await nextTick();
  if (!disposed) positionDialog.value?.showModal();
  return true;
}
function closePositionDialog() {
  positionDialog.value?.close();
  placement.value = null;
}
function confirmPosition() {
  const current = placement.value;
  const instance = editor.value;
  if (!current || !instance || props.disabled) return;
  const target = current.options[chosenPosition.value]?.position;
  if (target === undefined) return;
  // Map the destination through removal so moving an image does not shift it
  // to the wrong paragraph. A collapsed selection preserves selected text.
  const tr = instance.state.tr;
  const oldImages: { from: number; to: number }[] = [];
  instance.state.doc.descendants((node, pos) => {
    if (node.type.name === "image" && node.attrs.src === current.src)
      oldImages.push({ from: pos, to: pos + node.nodeSize });
  });
  for (const old of oldImages.reverse()) tr.delete(old.from, old.to);
  const mappedPosition = tr.mapping.map(target);
  // Keep moving and inserting together in one undoable transaction.
  instance
    .chain()
    .command(({ tr: chainTr }) => {
      for (const step of tr.steps) chainTr.step(step);
      return true;
    })
    .insertContentAt(Math.min(mappedPosition, tr.doc.content.size), {
      type: "image",
      attrs: { src: current.src, alt: current.alt },
    })
    .run();
  closePositionDialog();
  nextTick(() => instance.chain().focus().scrollIntoView().run());
}
function chooseUpload() {
  if (!editor.value || locked.value) return;
  uploadPosition = editor.value.state.selection.to;
  fileInput.value?.click();
}
defineExpose({ chooseImagePosition });
async function image(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || locked.value) return;
  uploading.value = true;
  emit("busy", true);
  try {
    const media = await upload(file);
    if (!disposed)
      await chooseImagePosition(media.url, media.name, uploadPosition);
  } catch (e) {
    if (!disposed) notify((e as Error).message, true);
  } finally {
    input.value = "";
    uploading.value = false;
    if (!disposed) emit("busy", false);
  }
}
function link() {
  const href = window.prompt("请输入 http/https 链接或本站路径");
  if (href && /^(https?:\/\/|\/[a-z])/i.test(href))
    editor.value
      ?.chain()
      .focus()
      .extendMarkRange("link")
      .setLink({ href })
      .run();
}
</script>
<template>
  <div v-if="editor" class="rich-editor" :aria-busy="uploading">
    <fieldset
      class="rich-toolbar"
      :disabled="locked"
      aria-label="正文格式工具栏"
    >
      <button
        type="button"
        @click="editor.chain().focus().undo().run()"
        title="撤销"
        aria-label="撤销"
      >
        ↶
      </button>
      <button
        type="button"
        @click="editor.chain().focus().redo().run()"
        title="重做"
        aria-label="重做"
      >
        ↷
      </button>
      <i aria-hidden="true"></i>
      <button
        type="button"
        :class="{ selected: editor.isActive('bold') }"
        @click="editor.chain().focus().toggleBold().run()"
        title="加粗"
        aria-label="加粗"
      >
        <b>B</b>
      </button>
      <button
        type="button"
        :class="{ selected: editor.isActive('italic') }"
        @click="editor.chain().focus().toggleItalic().run()"
        title="斜体"
        aria-label="斜体"
      >
        <em>I</em>
      </button>
      <button
        type="button"
        :class="{ selected: editor.isActive('underline') }"
        @click="editor.chain().focus().toggleUnderline().run()"
        title="下划线"
        aria-label="下划线"
      >
        <u>U</u>
      </button>
      <button
        type="button"
        :class="{ selected: editor.isActive('heading', { level: 2 }) }"
        @click="editor.chain().focus().toggleHeading({ level: 2 }).run()"
      >
        小标题
      </button>
      <button
        type="button"
        @click="editor.chain().focus().setParagraph().run()"
      >
        正文
      </button>
      <i aria-hidden="true"></i>
      <button
        type="button"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        • 列表
      </button>
      <button
        type="button"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        1. 编号
      </button>
      <button
        type="button"
        @click="editor.chain().focus().toggleBlockquote().run()"
      >
        ❝ 引用
      </button>
      <button type="button" @click="link">链接</button>
      <button
        type="button"
        class="image-tool"
        @mousedown.prevent
        @click="chooseUpload"
      >
        {{ uploading ? "图片上传中…" : "＋ 插入正文图片" }}
      </button>
      <button
        type="button"
        @click="editor.chain().focus().unsetAllMarks().run()"
      >
        清除格式
      </button>
      <input
        ref="fileInput"
        type="file"
        accept="image/png,image/jpeg"
        hidden
        @change="image"
      />
    </fieldset>
    <div v-if="selectedImage" class="image-actions">
      <span>已选中正文图片</span>
      <button
        type="button"
        class="btn"
        :disabled="locked"
        @mousedown.prevent
        @click="chooseImagePosition(selectedImage.src, selectedImage.alt)"
      >
        调整图片位置
      </button>
    </div>
    <EditorContent :editor="editor" />
    <div class="editor-foot" role="status">
      {{
        uploading
          ? "正在上传图片，完成后即可保存或发布。"
          : "先点击正文确定光标位置，再插入图片；点击已有图片可调整位置。支持 PNG / JPG，最大 10 MB。"
      }}
    </div>
    <dialog
      ref="positionDialog"
      class="image-position-dialog"
      aria-labelledby="image-position-title"
      @cancel.prevent="closePositionDialog"
    >
      <template v-if="placement">
        <div class="position-heading">
          <h3 id="image-position-title">
            {{ placement.moving ? "调整图片位置" : "选择图片插入位置" }}
          </h3>
          <button
            type="button"
            aria-label="关闭位置选择"
            @click="closePositionDialog"
          >
            ×
          </button>
        </div>
        <img
          class="position-preview"
          :src="placement.src"
          :alt="placement.alt"
        />
        <p>选择图片放在哪里，正文文字会保留。</p>
        <div
          class="position-options"
          role="radiogroup"
          aria-label="图片插入位置"
        >
          <label v-for="(option, index) in placement.options" :key="index">
            <input
              v-model="chosenPosition"
              type="radio"
              name="image-position"
              :value="index"
            />
            <span>{{ option.label }}</span>
          </label>
        </div>
        <div class="position-footer">
          <button type="button" class="btn" @click="closePositionDialog">
            取消
          </button>
          <button type="button" class="btn primary" @click="confirmPosition">
            {{ placement.moving ? "确认移动" : "插入这里" }}
          </button>
        </div>
      </template>
    </dialog>
  </div>
</template>
<style scoped>
.image-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: #edf6fc;
  font-size: 13px;
}
.image-position-dialog {
  width: min(560px, calc(100vw - 32px));
  max-height: 85vh;
  padding: 24px;
  border: 1px solid #dbe6ef;
  border-radius: 12px;
  color: #304d63;
  box-sizing: border-box;
}
.image-position-dialog::backdrop {
  background: #162c4266;
}
.position-heading,
.position-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.position-heading h3 {
  margin: 0;
  font-size: 18px;
}
.position-heading button {
  border: 0;
  background: none;
  font-size: 24px;
  cursor: pointer;
}
.position-preview {
  display: block;
  max-width: 100%;
  max-height: 120px;
  margin: 18px auto;
  object-fit: contain;
}
.image-position-dialog p {
  font-size: 13px;
  color: #778998;
}
.position-options {
  max-height: 32vh;
  overflow-y: auto;
  display: grid;
  gap: 8px;
  margin: 16px 0;
}
.position-options label {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e0e9f1;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.6;
}
.position-options label:has(input:checked) {
  border-color: #397fae;
  background: #edf6fc;
}
.position-options input {
  width: auto;
  flex-shrink: 0;
  margin-top: 4px;
}
.position-options span {
  overflow-wrap: anywhere;
}
.position-footer {
  justify-content: flex-end;
}
.rich-toolbar {
  border: 0;
  border-bottom: 1px solid #e3e9ef;
  margin: 0;
  min-width: 0;
}
.rich-toolbar .image-tool {
  color: #1e75ad;
  background: #e9f3fb;
  font-weight: 500;
}
.rich-toolbar button {
  min-height: 32px;
}
.editor-foot {
  line-height: 1.7;
}
:deep(.rich-body img) {
  display: block;
  height: auto;
  margin: 18px auto;
  border-radius: 4px;
}
:deep(.rich-body img.ProseMirror-selectednode) {
  outline: 2px solid #4595cf;
  outline-offset: 3px;
}
:deep(.rich-body[contenteditable="false"]) {
  opacity: 0.75;
}
</style>
