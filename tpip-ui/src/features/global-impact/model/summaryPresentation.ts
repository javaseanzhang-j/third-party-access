export function distributionPercent(count: number, total: number): number {
  if (count <= 0 || total <= 0) return 0
  return Math.min(100, Math.max(0, count / total * 100))
}
