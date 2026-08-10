<script setup lang="ts">
import type { BusinessMappingRow, MappingFieldType, SchemaFieldOption } from '../model/businessFieldMapping'

const rows = defineModel<BusinessMappingRow[]>({ required: true })
const props = defineProps<{ sourceFields: SchemaFieldOption[]; targetFields: SchemaFieldOption[]
  sourceLabel: string; targetLabel: string }>()
function add(): void { rows.value.push({ sourcePath: '', targetPath: '', targetType: 'STRING', required: false }) }
function remove(index: number): void { rows.value.splice(index, 1) }
function syncTargetType(row: BusinessMappingRow): void {
  const target = props.targetFields.find(item => item.path === row.targetPath)
  if (target) { row.targetType = target.type; row.required = row.required || target.required }
}
function fieldLabel(item: SchemaFieldOption): string { return `${item.label} · ${item.path}${item.required ? ' · 必填' : ''}` }
const typeOptions: Array<{ value: MappingFieldType; label: string }> = [
  { value: 'STRING', label: '文本' }, { value: 'NUMBER', label: '数字' }, { value: 'BOOLEAN', label: '是/否' },
  { value: 'OBJECT', label: '对象' }, { value: 'ARRAY', label: '数组' }
]
</script>

<template>
  <div class="business-mapping-editor">
    <div class="mapping-editor-head"><span>{{ sourceLabel }}</span><span>转换为</span><span>{{ targetLabel }}</span><span>目标类型</span><span>必填</span><el-button @click="add">增加映射</el-button></div>
    <el-empty v-if="!rows.length" description="暂无自动匹配字段，请点击增加映射" :image-size="54" />
    <div v-for="(row,index) in rows" :key="index" class="mapping-editor-row">
      <el-select v-model="row.sourcePath" filterable placeholder="选择来源字段"><el-option v-for="item in sourceFields" :key="item.path" :label="fieldLabel(item)" :value="item.path" /></el-select>
      <span>→</span>
      <el-select v-model="row.targetPath" filterable placeholder="选择目标字段" @change="syncTargetType(row)"><el-option v-for="item in targetFields" :key="item.path" :label="fieldLabel(item)" :value="item.path" /></el-select>
      <el-select v-model="row.targetType"><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-checkbox v-model="row.required" />
      <el-button link type="danger" @click="remove(index)">删除</el-button>
    </div>
  </div>
</template>
