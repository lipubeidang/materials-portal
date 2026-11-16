<script setup lang="ts">
import { watch, onBeforeUnmount, ref, computed, nextTick } from "vue";
import { NodeSelection } from "@tiptap/pm/state";
import { Extension, Mark, mergeAttributes } from "@tiptap/core";
import { useEditor, EditorContent } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import { upload, notify } from "../api";

const alignments = ["left", "center", "right", "justify"] as const;
const TextAlignment = Extension.create({
  name: "textAlignment",
  addGlobalAttributes() {
    return [
      {
        types: ["heading", "paragraph"],
        attributes: {
          textAlign: {
            default: null,
            parseHTML: (element) => {
              const value = element.getAttribute("data-text-align");
              return alignments.includes(value as (typeof alignments)[number])
                ? value
                : null;
            },
            renderHTML: (attributes) =>
              attributes.textAlign
                ? { "data-text-align": attributes.textAlign }
                : {},
          },
        },
      },
    ];
  },
});

const InlineFormat = Mark.create({
  name: "inlineFormat",
  addAttributes() {
    return {
      textColor: {
        default: null,
        parseHTML: (element) => element.getAttribute("data-text-color"),
        renderHTML: (attributes) =>
          attributes.textColor
            ? { "data-text-color": attributes.textColor }
            : {},
      },
      highlight: {
        default: null,
        parseHTML: (element) => element.getAttribute("data-highlight"),
        renderHTML: (attributes) =>
          attributes.highlight
            ? { "data-highlight": attributes.highlight }
            : {},
      },
      fontSize: {
        default: null,
        parseHTML: (element) => element.getAttribute("data-font-size"),
        renderHTML: (attributes) =>
          attributes.fontSize ? { "data-font-size": attributes.fontSize } : {},
      },
      fontFamily: {
        default: null,
        parseHTML: (element) => element.getAttribute("data-font-family"),
        renderHTML: (attributes) =>
          attributes.fontFamily
            ? { "data-font-family": attributes.fontFamily }
            : {},
      },
    };
  },
  parseHTML() {
    return [
      {
        tag: "span[data-text-color], span[data-highlight], span[data-font-size], span[data-font-family]",
      },
    ];
  },
  renderHTML({ HTMLAttributes }) {
    return ["span", mergeAttributes(HTMLAttributes), 0];
  },
});

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
const textColors = [
  { value: "", label: "自动颜色" },
  { value: "black", label: "黑色" },
  { value: "muted", label: "灰色" },
  { value: "red", label: "红色" },
  { value: "orange", label: "橙色" },
  { value: "amber", label: "琥珀色" },
  { value: "green", label: "绿色" },
  { value: "teal", label: "青色" },
  { value: "blue", label: "蓝色" },
  { value: "indigo", label: "靛蓝" },
  { value: "purple", label: "紫色" },
  { value: "pink", label: "粉色" },
];
const highlights = [
  { value: "", label: "无高亮" },
  { value: "yellow", label: "黄色高亮" },
  { value: "orange", label: "橙色高亮" },
  { value: "green", label: "绿色高亮" },
  { value: "blue", label: "蓝色高亮" },
  { value: "purple", label: "紫色高亮" },
  { value: "gray", label: "灰色高亮" },
];
const fontSizes = [
  { value: "", label: "默认字号" },
  { value: "xs", label: "小号 13px" },
  { value: "sm", label: "较小 15px" },
  { value: "base", label: "正文 17px" },
  { value: "lg", label: "大号 20px" },
  { value: "xl", label: "特大 24px" },
  { value: "2xl", label: "超大 30px" },
];
const fontFamilies = [
  { value: "", label: "默认字体" },
  { value: "yahei", label: "微软雅黑" },
  { value: "songti", label: "宋体" },
  { value: "heiti", label: "黑体" },
  { value: "kaiti", label: "楷体" },
  { value: "fangsong", label: "仿宋" },
];
let uploadPosition = 0;
let disposed = false;
const editor = useEditor({
  extensions: [StarterKit, TextAlignment, InlineFormat, Image],
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
function insertAttachmentLink(src: string, label: string) {
  const instance = editor.value;
  if (!instance || locked.value) return false;
  const chain = instance.chain().focus();
  if (!instance.state.selection.empty) {
    return chain.setLink({ href: src, target: "_blank" }).run();
  }
  return chain
    .insertContent({
      type: "text",
      text: label,
      marks: [
        {
          type: "link",
          attrs: {
            href: src,
            target: "_blank",
            rel: "noopener noreferrer",
          },
        },
      ],
    })
    .run();
}
defineExpose({ chooseImagePosition, insertAttachmentLink });
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
  const current = editor.value?.getAttributes("link").href || "";
  const href = window.prompt(
    "请输入 http/https 链接或本站路径；清空可移除链接",
    current,
  );
  if (href === null) return;
  if (!href.trim()) {
    editor.value?.chain().focus().extendMarkRange("link").unsetLink().run();
    return;
  }
  if (!/^(https?:\/\/|\/[a-zA-Z0-9])/i.test(href)) {
    notify("链接必须是 http/https 地址或本站路径", true);
    return;
  }
  editor.value
    ?.chain()
    .focus()
    .extendMarkRange("link")
    .setLink({ href: href.trim(), target: "_blank" })
    .run();
}
function setBlockType(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  const chain = editor.value?.chain().focus();
  if (!chain) return;
  if (value === "paragraph") chain.setParagraph().run();
  else chain.toggleHeading({ level: Number(value) as 2 | 3 | 4 }).run();
}
function setAlignment(value: string) {
  const chain = editor.value?.chain().focus();
  if (!chain) return;
  chain
    .updateAttributes("paragraph", { textAlign: value })
    .updateAttributes("heading", { textAlign: value })
    .run();
}
function setInlineFormat(
  attribute: "textColor" | "highlight" | "fontSize" | "fontFamily",
  value: string,
) {
  const instance = editor.value;
  if (!instance) return;
  const attributes = instance.getAttributes("inlineFormat");
  const next = { ...attributes, [attribute]: value || null };
  if (
    !next.textColor &&
    !next.highlight &&
    !next.fontSize &&
    !next.fontFamily
  ) {
    instance.chain().focus().unsetMark("inlineFormat").run();
    return;
  }
  instance.chain().focus().setMark("inlineFormat", next).run();
}
function clearFormatting() {
  editor.value?.chain().focus().unsetAllMarks().clearNodes().run();
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
      <label class="toolbar-select">
        <span class="sr-only">段落样式</span>
        <select
          aria-label="段落样式"
          :value="
            editor.isActive('heading', { level: 2 })
              ? '2'
              : editor.isActive('heading', { level: 3 })
                ? '3'
                : editor.isActive('heading', { level: 4 })
                  ? '4'
                  : 'paragraph'
          "
          @change="setBlockType"
        >
          <option value="paragraph">正文</option>
          <option value="2">二级标题</option>
          <option value="3">三级标题</option>
          <option value="4">四级标题</option>
        </select>
      </label>
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
        :class="{ selected: editor.isActive('strike') }"
        @click="editor.chain().focus().toggleStrike().run()"
        title="删除线"
        aria-label="删除线"
      >
        <s>S</s>
      </button>
      <i aria-hidden="true"></i>
      <button
        v-for="alignment in alignments"
        :key="alignment"
        type="button"
        :class="{ selected: editor.isActive({ textAlign: alignment }) }"
        :aria-label="
          {
            left: '左对齐',
            center: '居中',
            right: '右对齐',
            justify: '两端对齐',
          }[alignment]
        "
        :title="
          {
            left: '左对齐',
            center: '居中',
            right: '右对齐',
            justify: '两端对齐',
          }[alignment]
        "
        @click="setAlignment(alignment)"
      >
        {{ { left: "≡", center: "≣", right: "☰", justify: "▤" }[alignment] }}
      </button>
      <i aria-hidden="true"></i>
      <label class="toolbar-select font-select">
        <span class="sr-only">字体</span>
        <select
          aria-label="字体"
          :value="editor.getAttributes('inlineFormat').fontFamily || ''"
          @change="
            setInlineFormat(
              'fontFamily',
              ($event.target as HTMLSelectElement).value,
            )
          "
        >
          <option
            v-for="font in fontFamilies"
            :key="font.value"
            :value="font.value"
          >
            {{ font.label }}
          </option>
        </select>
      </label>
      <label class="toolbar-select size-select">
        <span class="sr-only">字号</span>
        <select
          aria-label="字号"
          :value="editor.getAttributes('inlineFormat').fontSize || ''"
          @change="
            setInlineFormat(
              'fontSize',
              ($event.target as HTMLSelectElement).value,
            )
          "
        >
          <option
            v-for="size in fontSizes"
            :key="size.value"
            :value="size.value"
          >
            {{ size.label }}
          </option>
        </select>
      </label>
      <label class="toolbar-select color-select">
        <span class="sr-only">文字颜色</span>
        <select
          aria-label="文字颜色"
          :value="editor.getAttributes('inlineFormat').textColor || ''"
          @change="
            setInlineFormat(
              'textColor',
              ($event.target as HTMLSelectElement).value,
            )
          "
        >
          <option
            v-for="color in textColors"
            :key="color.value"
            :value="color.value"
          >
            {{ color.label }}
          </option>
        </select>
      </label>
      <label class="toolbar-select color-select">
        <span class="sr-only">文字高亮</span>
        <select
          aria-label="文字高亮"
          :value="editor.getAttributes('inlineFormat').highlight || ''"
          @change="
            setInlineFormat(
              'highlight',
              ($event.target as HTMLSelectElement).value,
            )
          "
        >
          <option
            v-for="highlight in highlights"
            :key="highlight.value"
            :value="highlight.value"
          >
            {{ highlight.label }}
          </option>
        </select>
      </label>
      <i aria-hidden="true"></i>
      <button
        type="button"
        :class="{ selected: editor.isActive('bulletList') }"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        • 列表
      </button>
      <button
        type="button"
        :class="{ selected: editor.isActive('orderedList') }"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        1. 编号
      </button>
      <button
        type="button"
        :class="{ selected: editor.isActive('blockquote') }"
        @click="editor.chain().focus().toggleBlockquote().run()"
      >
        ❝ 引用
      </button>
      <button
        type="button"
        :class="{ selected: editor.isActive('link') }"
        @click="link"
      >
        {{ editor.isActive("link") ? "编辑链接" : "链接" }}
      </button>
      <button
        type="button"
        @click="editor.chain().focus().setHorizontalRule().run()"
      >
        分隔线
      </button>
      <button
        type="button"
        class="image-tool"
        @mousedown.prevent
        @click="chooseUpload"
      >
        {{ uploading ? "图片上传中…" : "＋ 插入正文图片" }}
      </button>
      <button type="button" @click="clearFormatting">清除格式</button>
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
.toolbar-select {
  display: inline-flex;
  margin: 0;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.toolbar-select select {
  width: auto;
  height: 32px;
  padding: 4px 26px 4px 8px;
  border: 1px solid #d6e0e8;
  border-radius: 3px;
  background: #fff;
  color: #536b7d;
  font-size: 12px;
}
.color-select select {
  max-width: 104px;
}
.font-select select,
.size-select select {
  max-width: 112px;
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
:deep(.rich-body [data-text-align="center"]) {
  text-align: center;
}
:deep(.rich-body [data-text-align="right"]) {
  text-align: right;
}
:deep(.rich-body [data-text-align="justify"]) {
  text-align: justify;
}
:deep(.rich-body [data-text-color="muted"]) {
  color: #6f7f8c;
}
:deep(.rich-body [data-text-color="black"]) {
  color: #172b3a;
}
:deep(.rich-body [data-text-color="blue"]) {
  color: #176fa6;
}
:deep(.rich-body [data-text-color="green"]) {
  color: #25795a;
}
:deep(.rich-body [data-text-color="red"]) {
  color: #b54242;
}
:deep(.rich-body [data-text-color="orange"]) {
  color: #c65d21;
}
:deep(.rich-body [data-text-color="amber"]) {
  color: #a96d00;
}
:deep(.rich-body [data-text-color="teal"]) {
  color: #167a7a;
}
:deep(.rich-body [data-text-color="indigo"]) {
  color: #4e5bb5;
}
:deep(.rich-body [data-text-color="purple"]) {
  color: #8152a5;
}
:deep(.rich-body [data-text-color="pink"]) {
  color: #b84578;
}
:deep(.rich-body [data-highlight="yellow"]) {
  background: #fff2a8;
}
:deep(.rich-body [data-highlight="blue"]) {
  background: #dceefa;
}
:deep(.rich-body [data-highlight="green"]) {
  background: #dff3e8;
}
:deep(.rich-body [data-highlight="orange"]) {
  background: #ffe5c7;
}
:deep(.rich-body [data-highlight="purple"]) {
  background: #eee1f7;
}
:deep(.rich-body [data-highlight="gray"]) {
  background: #e8edf1;
}
:deep(.rich-body [data-font-size="xs"]) {
  font-size: 13px;
}
:deep(.rich-body [data-font-size="sm"]) {
  font-size: 15px;
}
:deep(.rich-body [data-font-size="base"]) {
  font-size: 17px;
}
:deep(.rich-body [data-font-size="lg"]) {
  font-size: 20px;
}
:deep(.rich-body [data-font-size="xl"]) {
  font-size: 24px;
}
:deep(.rich-body [data-font-size="2xl"]) {
  font-size: 30px;
}
:deep(.rich-body [data-font-family="yahei"]) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}
:deep(.rich-body [data-font-family="songti"]) {
  font-family: SimSun, "Songti SC", serif;
}
:deep(.rich-body [data-font-family="heiti"]) {
  font-family: SimHei, "Heiti SC", sans-serif;
}
:deep(.rich-body [data-font-family="kaiti"]) {
  font-family: KaiTi, STKaiti, serif;
}
:deep(.rich-body [data-font-family="fangsong"]) {
  font-family: FangSong, STFangsong, serif;
}
:deep(.rich-body[contenteditable="false"]) {
  opacity: 0.75;
}
</style>
