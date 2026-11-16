<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { notify, send } from "../api";

const form = reactive({ subject: "", description: "" });
const busy = ref(false);
const descriptionCount = computed(() => form.description.length);

function handleReset() {
  form.subject = "";
  form.description = "";
}

async function handleSubmit() {
  busy.value = true;
  try {
    await send("/api/public/feedback", {
      subject: form.subject.trim(),
      description: form.description.trim(),
    });
    handleReset();
    notify("意见已提交，感谢您的反馈");
  } catch (error) {
    notify((error as Error).message, true);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="feedback-page container">
    <nav class="breadcrumbs feedback-breadcrumbs" aria-label="当前位置">
      <RouterLink to="/">首页</RouterLink><span aria-hidden="true">/</span>
      <span aria-current="page">意见反馈</span>
    </nav>
    <section class="feedback-panel" aria-labelledby="feedback-title">
      <header class="feedback-heading">
        <span>FEEDBACK</span>
        <h1 id="feedback-title">意见反馈</h1>
        <p>您的意见与建议将帮助我们持续完善网站服务。</p>
      </header>
      <p class="feedback-notice" role="note">
        <span aria-hidden="true">!</span>
        如果您在浏览网站过程中，对内容、功能或页面有任何意见与建议，欢迎提交给我们。我们将认真研究并持续改进服务。
      </p>
      <form class="feedback-form" @submit.prevent="handleSubmit" @reset="handleReset">
        <label for="feedback-subject">
          <span>意见主题 <b aria-hidden="true">*</b></span>
          <input
            id="feedback-subject"
            v-model="form.subject"
            maxlength="100"
            required
            autocomplete="off"
            placeholder="请输入意见主题"
          />
        </label>
        <label for="feedback-description">
          <span>意见描述 <b aria-hidden="true">*</b></span>
          <div class="feedback-textarea-wrap">
            <textarea
              id="feedback-description"
              v-model="form.description"
              maxlength="200"
              required
              placeholder="请输入意见描述，限 200 字以内"
              aria-describedby="feedback-description-count"
            ></textarea>
            <small id="feedback-description-count"
              >{{ descriptionCount }} / 200</small
            >
          </div>
        </label>
        <div class="feedback-actions">
          <button class="feedback-reset" type="reset" :disabled="busy">
            重置
          </button>
          <button class="feedback-submit" type="submit" :disabled="busy">
            {{ busy ? "正在提交…" : "提交" }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
