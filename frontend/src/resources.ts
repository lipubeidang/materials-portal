import { ref } from "vue";
import { api } from "./api";

export type Directory = { id: string; name: string; children: Directory[] };
export const directories = ref<Directory[]>([]);
export const directoriesLoading = ref(false);
export const directoriesError = ref("");
let pending: Promise<void> | undefined;
let loadedAt = 0;

export function loadDirectories(force = false): Promise<void> {
  if (pending) return pending;
  if (!force && loadedAt && Date.now() - loadedAt < 60_000)
    return Promise.resolve();
  directoriesLoading.value = true;
  directoriesError.value = "";
  pending = api<{ items: Directory[] }>("/api/public/resources/directories")
    .then((data) => {
      directories.value = data.items;
      loadedAt = Date.now();
    })
    .catch((error: Error) => {
      directoriesError.value = error.message;
    })
    .finally(() => {
      directoriesLoading.value = false;
      pending = undefined;
    });
  return pending;
}

export function directoryPath(nodes: Directory[], id: string): Directory[] {
  for (const node of nodes) {
    if (node.id === id) return [node];
    const path = directoryPath(node.children, id);
    if (path.length) return [node, ...path];
  }
  return [];
}
