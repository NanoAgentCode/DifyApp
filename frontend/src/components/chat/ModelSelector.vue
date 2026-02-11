<template>
  <el-select
    v-model="modelId"
    :placeholder="placeholder"
    class="model-selector-select"
    popper-class="model-selector-dropdown"
    size="small"
    clearable
    filterable
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
        <div class="model-selector-option">
          <div class="model-selector-option-main">
            <el-tag
              size="small"
              effect="plain"
              class="model-selector-tag"
              :style="getModelPlainStyle(model.id)"
            >
              {{ model.name }}
            </el-tag>
          </div>
          <el-tag v-if="model.isDefault" type="primary" size="small" class="model-selector-default-tag">
            默认
          </el-tag>
        </div>
      </template>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed } from 'vue'
import { getModelPlainStyle } from '@/utils/modelColor'

const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: null
  },
  models: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '选择模型'
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

<style scoped>
.model-selector-select {
  width: 200px;
}

.model-selector-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  min-height: 24px;
}

.model-selector-option-main {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex: 1;
  min-width: 0;
}

.model-selector-tag,
.model-selector-default-tag {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  font-weight: var(--tag-font-weight);
}

.model-selector-default-tag {
  margin-left: var(--spacing-sm);
}
</style>

<!-- 下拉为 teleported，需单独样式使选项行内标签垂直居中 -->
<style>
.model-selector-dropdown .el-select-dropdown__item {
  display: flex;
  align-items: center;
}
</style>

