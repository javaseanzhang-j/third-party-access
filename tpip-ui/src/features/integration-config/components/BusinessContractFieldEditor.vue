<script setup lang="ts">
import type { BusinessFieldType, BusinessMessageField } from '../model/businessMessageSchema'

const fields = defineModel<BusinessMessageField[]>({ required: true })
defineProps<{ title: string; description: string; pathExample: string }>()

function add(): void { fields.value.push({ path: '$.', name: '', type: 'STRING', required: false, description: '', example: '' }) }
function remove(index: number): void { fields.value.splice(index, 1) }
function typeText(value: BusinessFieldType): string {
  return { STRING: '文本', NUMBER: '数字', BOOLEAN: '是/否', OBJECT: '对象', ARRAY: '数组' }[value]
}
</script>

<template>
  <div class="standard-field-group">
    <div class="standard-field-heading"><div><strong>{{ title }}</strong><small>{{ description }}</small></div><el-button @click="add">增加字段</el-button></div>
    <el-empty v-if="!fields.length" description="当前标准报文没有字段，可以增加字段或保持空对象" :image-size="48" />
    <div v-for="(field,index) in fields" :key="index" class="standard-field-row">
      <el-input v-model="field.name" placeholder="中文含义" />
      <el-input v-model="field.path" :placeholder="pathExample" />
      <el-select v-model="field.type"><el-option v-for="item in ['STRING','NUMBER','BOOLEAN','OBJECT','ARRAY'] as BusinessFieldType[]" :key="item" :label="typeText(item)" :value="item" /></el-select>
      <el-input v-model="field.example" placeholder="示例值" />
      <el-input v-model="field.description" placeholder="字段说明（可选）" />
      <el-checkbox v-model="field.required">必填</el-checkbox>
      <el-button link type="danger" @click="remove(index)">删除</el-button>
    </div>
  </div>
</template>
