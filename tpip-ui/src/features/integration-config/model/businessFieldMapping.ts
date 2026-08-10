export type MappingFieldType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'OBJECT' | 'ARRAY'

export interface SchemaFieldOption {
  path: string
  label: string
  type: MappingFieldType
  required: boolean
}

export interface BusinessMappingRow {
  sourcePath: string
  targetPath: string
  targetType: MappingFieldType
  required: boolean
}

interface JsonSchema {
  type?: string
  title?: string
  description?: string
  properties?: Record<string, JsonSchema>
  required?: string[]
  example?: unknown
  default?: unknown
  items?: JsonSchema
}

export function extractSchemaFields(schema: unknown): SchemaFieldOption[] {
  if (!schema || typeof schema !== 'object' || Array.isArray(schema)) return []
  return walk(schema as JsonSchema, '$', true)
}

export function autoMatchFields(source: SchemaFieldOption[], target: SchemaFieldOption[]): BusinessMappingRow[] {
  if (!source.length || !target.length) return []
  const available = new Set(target.map(item => item.path))
  return source.flatMap(item => {
    const terminal = item.path.split('.').at(-1)?.toLowerCase()
    const exact = target.find(candidate => candidate.path.split('.').at(-1)?.toLowerCase() === terminal)
    if (!exact || !available.delete(exact.path)) return []
    return [{ sourcePath: item.path, targetPath: exact.path, targetType: exact.type, required: item.required || exact.required }]
  })
}

export function exampleFromSchema(schema: unknown): unknown {
  if (!schema || typeof schema !== 'object' || Array.isArray(schema)) return {}
  return schemaExample(schema as JsonSchema)
}

export function executeMappingPreview(source: unknown, rows: BusinessMappingRow[]): unknown {
  const output: Record<string, unknown> = {}
  for (const [index, row] of rows.entries()) {
    if (!row.sourcePath || !row.targetPath) throw new Error(`第 ${index + 1} 条映射尚未选择完整字段`)
    const value = readPath(source, row.sourcePath)
    if (value === undefined) {
      if (row.required) throw new Error(`必填来源字段 ${row.sourcePath} 在样例中不存在`)
      continue
    }
    writePath(output, row.targetPath, convert(value, row.targetType, row.targetPath))
  }
  return output
}

function walk(schema: JsonSchema, prefix: string, inheritedRequired: boolean): SchemaFieldOption[] {
  const required = new Set(schema.required ?? [])
  return Object.entries(schema.properties ?? {}).flatMap(([name, value]) => {
    const path = `${prefix}.${name}`
    const isRequired = inheritedRequired && required.has(name)
    if (value.type === 'object' && value.properties && Object.keys(value.properties).length) {
      return walk(value, path, isRequired)
    }
    return [{ path, label: value.title?.trim() || value.description?.trim() || name,
      type: mappingType(value.type), required: isRequired }]
  })
}

function mappingType(value?: string): MappingFieldType {
  return { string: 'STRING', integer: 'NUMBER', number: 'NUMBER', boolean: 'BOOLEAN', object: 'OBJECT', array: 'ARRAY' }[value ?? ''] as MappingFieldType ?? 'STRING'
}

function schemaExample(schema: JsonSchema): unknown {
  if (schema.example !== undefined) return schema.example
  if (schema.default !== undefined) return schema.default
  if (schema.type === 'object' || schema.properties) return Object.fromEntries(
    Object.entries(schema.properties ?? {}).map(([name, child]) => [name, schemaExample(child)]))
  if (schema.type === 'array') return schema.items ? [schemaExample(schema.items)] : []
  if (schema.type === 'boolean') return false
  if (schema.type === 'integer' || schema.type === 'number') return 0
  return ''
}

function segments(path: string): string[] {
  if (!/^\$\.[A-Za-z_][A-Za-z0-9_-]*(?:\.[A-Za-z_][A-Za-z0-9_-]*)*$/.test(path))
    throw new Error(`业务化映射仅支持对象字段路径：${path}`)
  return path.slice(2).split('.')
}

function readPath(value: unknown, path: string): unknown {
  let current = value
  for (const segment of segments(path)) {
    if (!current || typeof current !== 'object' || Array.isArray(current)) return undefined
    current = (current as Record<string, unknown>)[segment]
  }
  return current
}

function writePath(target: Record<string, unknown>, path: string, value: unknown): void {
  const values = segments(path); let current = target
  for (const segment of values.slice(0, -1)) {
    if (!current[segment] || typeof current[segment] !== 'object' || Array.isArray(current[segment])) current[segment] = {}
    current = current[segment] as Record<string, unknown>
  }
  current[values.at(-1)!] = value
}

function convert(value: unknown, type: MappingFieldType, path: string): unknown {
  if (type === 'STRING') return typeof value === 'string' ? value : String(value)
  if (type === 'NUMBER') {
    const number = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(number)) throw new Error(`字段 ${path} 无法转换为数字`)
    return number
  }
  if (type === 'BOOLEAN') {
    if (typeof value === 'boolean') return value
    if (value === 'true' || value === '1' || value === 1) return true
    if (value === 'false' || value === '0' || value === 0) return false
    throw new Error(`字段 ${path} 无法转换为是/否`)
  }
  if (type === 'ARRAY' && !Array.isArray(value)) throw new Error(`字段 ${path} 必须是数组`)
  if (type === 'OBJECT' && (!value || typeof value !== 'object' || Array.isArray(value))) throw new Error(`字段 ${path} 必须是对象`)
  return value
}
