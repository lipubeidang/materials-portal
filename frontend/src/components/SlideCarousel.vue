<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from "vue";

const props = withDefaults(
  defineProps<{
    count: number;
    label: string;
    interval?: number;
  }>(),
  { interval: 7000 },
);
const index = ref(0);
const direction = ref("next");
const moving = ref(false);
const hovered = ref(false);
const focused = ref(false);
const reducedMotion = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;
let motion: MediaQueryList;
let touchStart: { x: number; y: number } | undefined;

function go(target: number, step = target > index.value ? 1 : -1) {
  if (moving.value || props.count < 2) return;
  const next = (target + props.count) % props.count;
  if (next === index.value) return;
  direction.value = step > 0 ? "next" : "previous";
  moving.value = true;
  index.value = next;
}
function move(step: number) {
  go(index.value + step, step);
}
function touchEnd(event: TouchEvent) {
  if (!touchStart) return;
  const dx = event.changedTouches[0]!.clientX - touchStart.x;
  const dy = event.changedTouches[0]!.clientY - touchStart.y;
  if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) move(dx < 0 ? 1 : -1);
  touchStart = undefined;
}
function updateMotion() {
  reducedMotion.value = motion.matches;
}
watch(
  () => props.count,
  () => {
    index.value = 0;
    moving.value = false;
  },
);
onMounted(() => {
  motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  updateMotion();
  motion.addEventListener("change", updateMotion);
  timer = setInterval(() => {
    if (
      !hovered.value &&
      !focused.value &&
      !document.hidden &&
      !reducedMotion.value
    )
      move(1);
  }, props.interval);
});
onBeforeUnmount(() => {
  clearInterval(timer);
  motion?.removeEventListener("change", updateMotion);
});
</script>

<template>
  <section
    class="slide-carousel"
    :aria-label="label"
    aria-roledescription="轮播"
    tabindex="0"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
    @focusin="focused = true"
    @focusout="
      focused = ($event.currentTarget as HTMLElement).contains(
        $event.relatedTarget as Node,
      )
    "
    @keydown.left.prevent="move(-1)"
    @keydown.right.prevent="move(1)"
    @touchstart.passive="
      touchStart = {
        x: $event.touches[0]!.clientX,
        y: $event.touches[0]!.clientY,
      }
    "
    @touchend.passive="touchEnd"
    @touchcancel="touchStart = undefined"
  >
    <div class="carousel-viewport">
      <Transition
        :name="'carousel-' + direction"
        @after-enter="moving = false"
        @enter-cancelled="moving = false"
        @before-leave="(el) => el.setAttribute('inert', '')"
      >
        <div
          :key="index"
          class="carousel-slide"
          role="group"
          aria-roledescription="幻灯片"
          :aria-label="`${index + 1} / ${Math.max(count, 1)}`"
        >
          <slot :index="index" />
        </div>
      </Transition>
      <template v-if="count > 1">
        <button
          type="button"
          class="carousel-arrow carousel-previous"
          :aria-label="label + '：上一张'"
          :aria-disabled="moving"
          @click="move(-1)"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="m15 5-7 7 7 7"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <button
          type="button"
          class="carousel-arrow carousel-next"
          :aria-label="label + '：下一张'"
          :aria-disabled="moving"
          @click="move(1)"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="m9 5 7 7-7 7"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </template>
    </div>
    <div
      v-if="count > 1"
      class="carousel-dots"
      :aria-label="label + '：选择图片'"
    >
      <button
        v-for="i in count"
        :key="i"
        type="button"
        :class="{ active: i - 1 === index }"
        :aria-label="`第 ${i} 张`"
        :aria-pressed="i - 1 === index"
        @click="go(i - 1)"
      ></button>
    </div>
    <slot name="caption" :index="index" />
  </section>
</template>

<style>
.slide-carousel {
  position: relative;
  min-width: 0;
}
.carousel-viewport {
  position: relative;
  overflow: hidden;
  touch-action: pan-y;
}
.carousel-slide {
  position: relative;
  width: 100%;
}
.carousel-next-enter-active,
.carousel-next-leave-active,
.carousel-previous-enter-active,
.carousel-previous-leave-active {
  transition: transform 520ms cubic-bezier(0.22, 0.61, 0.36, 1);
}
.carousel-next-leave-active,
.carousel-previous-leave-active {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.carousel-next-enter-from,
.carousel-previous-leave-to {
  transform: translateX(100%);
}
.carousel-next-leave-to,
.carousel-previous-enter-from {
  transform: translateX(-100%);
}
.carousel-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 3;
  display: grid;
  place-items: center;
  width: 42px;
  height: 56px;
  border-radius: 5px;
  color: white;
  background: #08294e70;
  cursor: pointer;
}
.carousel-arrow:hover {
  background: #0753a8d9;
}
.carousel-arrow svg {
  width: 24px;
  height: 24px;
}
.carousel-previous {
  left: 16px;
}
.carousel-next {
  right: 16px;
}
.carousel-dots {
  display: flex;
  justify-content: center;
  gap: 3px;
}
.carousel-dots button {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  padding: 0;
}
.carousel-dots button::after {
  content: "";
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #afc7df;
}
.carousel-dots button.active::after {
  background: #1767cf;
}
.slide-carousel:focus-visible,
.carousel-arrow:focus-visible,
.carousel-dots button:focus-visible {
  outline: 3px solid #ffad33;
  outline-offset: -3px;
}
@media (prefers-reduced-motion: reduce) {
  .carousel-next-enter-active,
  .carousel-next-leave-active,
  .carousel-previous-enter-active,
  .carousel-previous-leave-active {
    transition-duration: 0.01ms;
  }
}
@media (max-width: 700px) {
  .carousel-arrow {
    width: 32px;
    height: 46px;
  }
  .carousel-previous {
    left: 5px;
  }
  .carousel-next {
    right: 5px;
  }
}
</style>
