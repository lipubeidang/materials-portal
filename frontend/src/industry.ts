import { categoryPath, type Row } from "./api";

export const industryRegions = [
  { value: "domestic", label: "国内动态" },
  { value: "international", label: "国际动态" },
];
export const industryTopics = [
  { value: "policy", label: "政策文件" },
  { value: "action", label: "行动计划" },
  { value: "science", label: "科技动态" },
  { value: "progress", label: "行业进展" },
];
export function topicsFor(region: string) {
  return region === "international" ? industryTopics.slice(2) : industryTopics;
}
export function industryLabel(item: Row) {
  return `${industryRegions.find((r) => r.value === (item.industryRegion || "domestic"))?.label || "国内动态"} / ${industryTopics.find((t) => t.value === (item.industryTopic || "science"))?.label || "科技动态"}`;
}
export function industryPath(item: Row) {
  return {
    path: categoryPath("industry"),
    query: {
      region: item.industryRegion || "domestic",
      topic: item.industryTopic || "science",
    },
  };
}
export function isIndustryCategory(id: string, categories: Row[]) {
  const seen = new Set<string>();
  while (id && !seen.has(id)) {
    if (id === "industry") return true;
    seen.add(id);
    id = categories.find((c) => c.id === id)?.parent_id || "";
  }
  return false;
}
