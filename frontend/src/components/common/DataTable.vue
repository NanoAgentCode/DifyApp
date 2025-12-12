<template>
  <div class="data-table">
    <!-- 搜索栏 -->
    <div v-if="showSearch" class="search-bar">
      <el-input
        v-model="searchKeyword"
        :placeholder="searchPlaceholder"
        clearable
        style="width: 300px"
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <slot name="search-extra"></slot>
    </div>

    <!-- 操作栏 -->
    <div v-if="showActions || $slots.actions" class="action-bar">
      <slot name="actions">
        <el-button v-if="showCreate" type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ createButtonText }}
        </el-button>
      </slot>
    </div>

    <!-- 表格 -->
    <el-table
      :data="tableData"
      v-loading="loading"
      stripe
      :row-key="rowKey"
      :default-sort="defaultSort"
      @selection-change="handleSelectionChange"
    >
      <el-table-column v-if="showSelection" type="selection" width="55" />
      <slot></slot>
    </el-table>

    <!-- 分页 -->
    <div v-if="showPagination" class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { useDebounceFn } from '@/utils/debounce'

const props = defineProps({
  // 数据
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  // 搜索
  showSearch: {
    type: Boolean,
    default: true
  },
  searchPlaceholder: {
    type: String,
    default: '请输入搜索关键词'
  },
  searchDebounce: {
    type: Number,
    default: 300
  },
  // 操作
  showActions: {
    type: Boolean,
    default: true
  },
  showCreate: {
    type: Boolean,
    default: true
  },
  createButtonText: {
    type: String,
    default: '创建'
  },
  // 表格
  rowKey: {
    type: [String, Function],
    default: 'id'
  },
  defaultSort: {
    type: Object,
    default: null
  },
  showSelection: {
    type: Boolean,
    default: false
  },
  // 分页
  showPagination: {
    type: Boolean,
    default: true
  },
  total: {
    type: Number,
    default: 0
  },
  pageSize: {
    type: Number,
    default: 10
  },
  pageSizes: {
    type: Array,
    default: () => [10, 20, 50, 100]
  }
})

const emit = defineEmits([
  'search',
  'create',
  'selection-change',
  'page-change',
  'size-change'
])

// 搜索
const searchKeyword = ref('')
const debouncedSearch = useDebounceFn((keyword) => {
  emit('search', keyword)
}, props.searchDebounce)

function handleSearch() {
  debouncedSearch(searchKeyword.value)
}

// 分页
const currentPage = ref(1)

function handlePageChange(page) {
  currentPage.value = page
  emit('page-change', page)
}

function handleSizeChange(size) {
  emit('size-change', size)
}

// 选择
function handleSelectionChange(selection) {
  emit('selection-change', selection)
}

// 创建
function handleCreate() {
  emit('create')
}

// 计算表格数据（支持本地搜索）
const tableData = computed(() => {
  if (!props.showSearch || !searchKeyword.value) {
    return props.data
  }
  // 如果提供了数据，可以进行本地过滤
  // 否则由父组件处理搜索
  return props.data
})

// 监听外部数据变化，重置搜索
watch(() => props.data, () => {
  // 可以在这里添加逻辑
}, { deep: true })
</script>

<style scoped>
.data-table {
  padding: 20px;
}

.search-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-bar {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>

