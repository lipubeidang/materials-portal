<script setup lang="ts">
import { computed } from "vue";
import type { Directory } from "../resources";

const props = defineProps<{
  node: Directory;
  expanded: Set<string>;
  selectedId: string;
}>();
const emit = defineEmits<{
  select: [id: string];
  toggle: [id: string];
}>();
const open = computed(() => props.expanded.has(props.node.id));
</script>

<template>
  <li class="resource-tree-node">
    <div
      class="resource-tree-row"
      :class="{ selected: selectedId === node.id }"
    >
      <button
        v-if="node.children.length"
        type="button"
        class="resource-tree-toggle"
        :aria-label="`${open ? '收起' : '展开'}${node.name}`"
        :aria-expanded="open"
        :aria-controls="`directory-children-${node.id}`"
        @click="emit('toggle', node.id)"
      >
        <span :class="{ open }" aria-hidden="true">▸</span>
      </button>
      <span v-else class="resource-tree-spacer" aria-hidden="true"></span>
      <button
        type="button"
        class="resource-tree-name"
        :aria-current="selectedId === node.id ? 'true' : undefined"
        :title="node.name"
        @click="emit('select', node.id)"
      >
        {{ node.name }}
      </button>
    </div>
    <ul
      v-if="node.children.length && open"
      :id="`directory-children-${node.id}`"
      class="resource-tree-children"
    >
      <ResourceTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :expanded="expanded"
        :selected-id="selectedId"
        @select="emit('select', $event)"
        @toggle="emit('toggle', $event)"
      />
    </ul>
  </li>
</template>
