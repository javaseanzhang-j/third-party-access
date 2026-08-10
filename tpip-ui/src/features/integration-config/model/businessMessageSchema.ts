export type BusinessFieldType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT' | 'ARRAY'

export interface BusinessMessageField {
  path: string
  name: string
  type: BusinessFieldType
  required: boolean
  description: string
  example: string
}

interface SchemaNode {
  type: string
  title?: string
  description?: string
  properties?: Record<string, SchemaNode>
  required?: string[]
  items?: Record<string, unknown>
  example?: unknown
  additionalProperties?: boolean
}

const pathPattern = /^\$\.[A-Za-z_][A-Za-z0-9_-]*(?:\.[A-Za-z_][A-Za-z0-9_-]*)*$/

export function buildBusinessMessageSchema(fields: BusinessMessageField[]): Record<string, unknown> | null {
  if (!fields.length) return null
  const root: SchemaNode = { type: 'object', properties: {}, additionalProperties: false }
  const used = new Set<string>()
  for (const field of fields) {
    const path = field.path.trim()
    if (!pathPattern.test(path)) throw new Error(`字段路径“${path || '空'}”格式不正确，请使用 $.field 或 $.data.field`)
    if (!field.name.trim()) throw new Error(`字段 ${path} 缺少业务名称`)
    if (used.has(path)) throw new Error(`字段路径 ${path} 重复`)
    used.add(path)
    const segments = path.slice(2).split('.')
    let parent = root
    for (let index = 0; index < segments.length; index += 1) {
      const segment = segments[index]!
      const last = index === segments.length - 1
      parent.properties ??= {}
      if (!last) {
        const existing = parent.properties[segment]
        if (existing && existing.type !== 'object') throw new Error(`字段路径 ${path} 与已有非对象字段冲突`)
        parent.properties[segment] ??= { type: 'object', properties: {}, additionalProperties: false }
        if (field.required) {
          parent.required ??= []
          if (!parent.required.includes(segment)) parent.required.push(segment)
        }
        parent = parent.properties[segment]!
        continue
      }
      if (parent.properties[segment]) throw new Error(`字段路径 ${path} 与已有对象路径冲突`)
      parent.properties[segment] = schemaFor(field)
      if (field.required) {
        parent.required ??= []
        parent.required.push(segment)
      }
    }
  }
  return root as unknown as Record<string, unknown>
}

export function buildBusinessMessageExample(fields: BusinessMessageField[]): Record<string, unknown> | null {
  if (!fields.length) return null
  const result: Record<string, unknown> = {}
  for (const field of fields) {
    const segments = field.path.trim().slice(2).split('.')
    let parent = result
    for (let index = 0; index < segments.length - 1; index += 1) {
      const segment = segments[index]!
      const existing = parent[segment]
      if (!existing || typeof existing !== 'object' || Array.isArray(existing)) parent[segment] = {}
      parent = parent[segment] as Record<string, unknown>
    }
    parent[segments.at(-1)!] = exampleValue(field)
  }
  return result
}

export function countSchemaFields(value: unknown): number {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return 0
  const schema = value as { properties?: Record<string, unknown> }
  return Object.values(schema.properties ?? {}).reduce<number>((count, item) => {
    const child = item && typeof item === 'object' && !Array.isArray(item)
      ? item as { type?: string; title?: string; properties?: Record<string, unknown> } : null
    const nested = child?.type === 'object' && child.properties && !child.title ? countSchemaFields(child) : 0
    return count + (nested || 1)
  }, 0)
}

function schemaFor(field: BusinessMessageField): SchemaNode {
  const base: SchemaNode = { type: field.type.toLowerCase(), title: field.name.trim() }
  if (field.description.trim()) base.description = field.description.trim()
  if (field.type === 'OBJECT') { base.properties = {}; base.additionalProperties = true }
  if (field.type === 'ARRAY') base.items = {}
  if (field.example.trim()) base.example = exampleValue(field)
  return base
}

function exampleValue(field: BusinessMessageField): unknown {
  const value = field.example.trim()
  if (!value) return field.type === 'STRING' ? '' : field.type === 'NUMBER' ? 0
    : field.type === 'BOOLEAN' ? false : field.type === 'ARRAY' ? [] : {}
  if (field.type === 'STRING') return value
  if (field.type === 'NUMBER') {
    const parsed = Number(value)
    if (!Number.isFinite(parsed)) throw new Error(`字段 ${field.path} 的示例不是有效数字`)
    return parsed
  }
  if (field.type === 'BOOLEAN') {
    if (!['true', 'false'].includes(value.toLowerCase())) throw new Error(`字段 ${field.path} 的布尔示例只能是 true 或 false`)
    return value.toLowerCase() === 'true'
  }
  try { return JSON.parse(value) as unknown }
  catch { throw new Error(`字段 ${field.path} 的对象或数组示例必须是合法 JSON`) }
}
