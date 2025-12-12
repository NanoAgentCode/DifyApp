<template>
  <el-select
    v-model="modelId"
    placeholder="选择模型"
    style="width: 200px"
    size="small"
    clearable
    :disabled="disabled"
    @change="handleChange"
  >
    <el-option
      v-for="model in models"
      :key="model.id"
      :label="model.name"
      :value="model.id"
    >
      <template #default>
        <div style="display: flex; justify-content: space-between; align-items: center; width: 100%">
          <div style="display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0">
            <el-tag 
              size="small"
              :style="getModelStyle(model.id)"
              style="flex-shrink: 0"
            >
              {{ model.name }}
            </el-tag>
          </div>
          <el-tag v-if="model.isDefault" type="primary" size="small" style="margin-left: 8px; flex-shrink: 0">
            默认
          </el-tag>
        </div>
      </template>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed } from 'vue'
import { getModelStyle } from '@/utils/modelColor'

const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: null
  },
  models: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const modelId = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit('update:modelValue', value)
  }
})

const handleChange = (value) => {
  emit('change', value)
}
</script>

