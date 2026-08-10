<script setup lang="ts">
import type { JobStatus, ItemStatus } from '../api/globalImpactApi'
import { itemStatusLabel, jobStatusLabel, statusType } from '../model/presentation'

const props = defineProps<{ status: JobStatus | ItemStatus }>()
const label = props.status in jobStatusLabel
  ? jobStatusLabel[props.status as JobStatus]
  : itemStatusLabel[props.status as ItemStatus]
</script>
<template><el-tag class="status-tag" :type="statusType(status)" effect="light">{{ label }}</el-tag></template>
