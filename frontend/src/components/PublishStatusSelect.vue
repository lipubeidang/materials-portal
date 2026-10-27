<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

const model = defineModel<string>({ required: true });
const options = [
  { value: "", label: "全部有效内容" },
  { value: "DRAFT", label: "草稿" },
  { value: "PUBLISHED", label: "已发布" },
  { value: "DELETED", label: "回收站" },
];
const root = ref<HTMLElement>();
const open = ref(false);
const active = ref(0);
const selected = computed(() =>
  Math.max(
    0,
    options.findIndex((o) => o.value === model.value),
  ),
);
function toggle() {
  active.value = selected.value;
  open.value = !open.value;
}
function choose(index: number) {
  model.value = options[index]!.value;
  open.value = false;
}
function keydown(event: KeyboardEvent) {
  if (event.key === "Tab") {
    open.value = false;
    return;
  }
  if (event.key === "Escape") {
    if (open.value) event.preventDefault();
    open.value = false;
    return;
  }
  if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
    event.preventDefault();
    if (!open.value) {
      active.value = selected.value;
      open.value = true;
    } else if (event.key === "ArrowDown")
      active.value = Math.min(options.length - 1, active.value + 1);
    else if (event.key === "ArrowUp")
      active.value = Math.max(0, active.value - 1);
    if (event.key === "Home") active.value = 0;
    if (event.key === "End") active.value = options.length - 1;
  } else if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    if (open.value) choose(active.value);
    else toggle();
  }
}
function outside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false;
}
onMounted(() => document.addEventListener("pointerdown", outside));
onBeforeUnmount(() => document.removeEventListener("pointerdown", outside));
</script>

<template>
  <div ref="root" class="publish-status-field">
    <label id="publish-status-label" for="publish-status-trigger"
      >发布状态</label
    >
    <div class="publish-status-control">
      <button
        id="publish-status-trigger"
        type="button"
        role="combobox"
        aria-labelledby="publish-status-label"
        aria-haspopup="listbox"
        aria-controls="publish-status-options"
        :aria-expanded="open"
        :aria-activedescendant="
          open ? 'publish-status-option-' + active : undefined
        "
        class="publish-status-trigger"
        @click="toggle"
        @keydown="keydown"
        @blur="open = false"
      >
        <span>{{ options[selected]!.label }}</span>
        <svg viewBox="0 0 16 16" :class="{ open }" aria-hidden="true">
          <path d="m4 6 4 4 4-4" />
        </svg>
      </button>
      <ul
        v-if="open"
        id="publish-status-options"
        role="listbox"
        aria-labelledby="publish-status-label"
        class="publish-status-options"
      >
        <li
          v-for="(option, index) in options"
          :id="'publish-status-option-' + index"
          :key="option.value"
          role="option"
          :aria-selected="model === option.value"
          :class="{
            active: active === index,
            selected: model === option.value,
          }"
          @pointerdown.prevent
          @pointermove="active = index"
          @click="choose(index)"
        >
          <span>{{ option.label }}</span
          ><span v-if="model === option.value" aria-hidden="true">✓</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.publish-status-field {
  flex: 1;
  min-width: 0;
  max-width: 470px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.publish-status-control {
  position: relative;
}
.publish-status-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  border: 1px solid #dce2e8;
  border-radius: 3px;
  padding: 10px 12px;
  background: white;
  color: #29394a;
  text-align: left;
}
.publish-status-trigger:focus-visible,
.publish-status-trigger[aria-expanded="true"] {
  outline: none;
  border-color: #458fd0;
  box-shadow: 0 0 0 3px #2881c911;
}
.publish-status-trigger svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
}
.publish-status-trigger svg.open {
  transform: rotate(180deg);
}
.publish-status-options {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 30;
  margin: 0;
  padding: 4px;
  list-style: none;
  background: white;
  border: 1px solid #dce2e8;
  border-radius: 5px;
  box-shadow: 0 6px 20px #26374626;
}
.publish-status-options li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 38px;
  padding: 8px 10px;
  border-radius: 3px;
  cursor: pointer;
}
.publish-status-options li.selected {
  color: #2479b0;
}
.publish-status-options li.active {
  background: #edf6fd;
  color: #10488f;
}
@media (max-width: 700px) {
  .publish-status-field {
    max-width: none;
  }
}
</style>
