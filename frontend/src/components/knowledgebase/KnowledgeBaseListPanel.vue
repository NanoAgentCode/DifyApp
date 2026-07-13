<template>
    <el-card>
      <template v-if="userMode" #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="primary" link @click="$emit('back')" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>我的知识库</span>
          </div>
          <div class="header-right">
            <el-button type="success" @click="$emit('import')">
              <el-icon><UploadFilled /></el-icon>
              导入知识库
            </el-button>
            <el-button type="primary" @click="$emit('create')">
              <el-icon><Plus /></el-icon>
              创建知识库
            </el-button>
          </div>
        </div>
      </template>
      <!-- 搜索栏 -->
      <div class="search-bar">
        <div class="search-left">
          <el-input
            v-model="searchKeywordModel"
            placeholder="搜索知识库名称或描述"
            clearable
            style="width: 300px"
            @input="$emit('search')"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select
            v-model="filterVectorStoreTypeModel"
            placeholder="筛选向量库"
            clearable
            style="width: 150px"
            @change="$emit('filter')"
          >
            <el-option label="全部" value="" />
            <el-option label="Qdrant" value="qdrant" />
            <el-option label="FAISS" value="faiss" />
            <el-option label="Milvus" value="milvus" />
            <el-option label="Chroma" value="chroma" />
            <el-option label="Weaviate" value="weaviate" />
            <el-option label="PgVector" value="pgvector" />
          </el-select>
          <el-select
            v-model="filterStatusModel"
            placeholder="筛选状态"
            clearable
            style="width: 150px"
            @change="$emit('filter')"
          >
            <el-option label="全部" value="" />
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </div>
        <div v-if="!userMode" class="search-right">
          <el-button type="success" @click="$emit('import')">
            <el-icon><UploadFilled /></el-icon>
            导入知识库
          </el-button>
          <el-button type="primary" @click="$emit('create')">
            <el-icon><Plus /></el-icon>
            创建知识库
          </el-button>
        </div>
      </div>

      <!-- 知识库列表 -->
      <div class="table-container" style="padding: 0 20px;">
        <el-table
          :data="tableKnowledgeBases"
          v-loading="loading"
          stripe
          :lazy="false"
          :row-key="row => row.id"
          :default-sort="{ prop: 'createTime', order: 'descending' }"
        >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="知识库名称" min-width="200">
          <template #default="{ row }">
            <div class="kb-name-cell">
              <el-icon class="kb-icon"><Document /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column label="文档数量" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info">{{ row.documentCount || 0 }} 个</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!userMode" label="可见性" width="100" align="center">
          <template #default="{ row }">
            <el-tooltip :content="row.isPublic ? '公开' : '私有'" placement="top">
              <el-icon :size="20" :color="row.isPublic ? '#67c23a' : '#909399'">
                <Unlock v-if="row.isPublic" />
                <Lock v-else />
              </el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tooltip :content="getStatusText(row.status)" placement="top">
              <el-icon :size="20" :color="isActive(row.status) ? '#67c23a' : '#909399'">
                <Check v-if="isActive(row.status)" />
                <Close v-else />
              </el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="向量化模型" width="180" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="getEmbeddingModelName(row.embeddingModelId)"
              size="small"
              effect="plain"
              class="kb-embedding-model-tag"
              :style="getModelPlainStyle(row.embeddingModelId)"
            >
              {{ getEmbeddingModelName(row.embeddingModelId) }}
            </el-tag>
            <span v-else class="kb-cell-empty">-</span>
          </template>
        </el-table-column>
        <el-table-column label="向量存储" width="180" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getVectorStoreTypeTag(row.vectorStoreType)"
              size="small"
            >
              {{ getVectorStoreInstanceName(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
            <el-table-column label="操作" width="280" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-buttons-row">
                  <el-tooltip content="可以通过查看详情看摘要" placement="top">
                    <el-button
                      size="small"
                      type="warning"
                      @click="$emit('summary', row)"
                    >
                      <el-icon><DocumentCopy /></el-icon>
                      摘要
                    </el-button>
                  </el-tooltip>
                  <el-button size="small" type="danger" @click="$emit('delete', row)">删除</el-button>
                  <el-dropdown @command="command => $emit('dropdown', { command, row })">
                    <el-button size="small" type="primary">
                      更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="view">
                          <el-icon><View /></el-icon>
                          查看详情
                        </el-dropdown-item>
                        <el-dropdown-item command="edit">
                          <el-icon><Edit /></el-icon>
                          编辑信息
                        </el-dropdown-item>
                        <el-dropdown-item command="documents">
                          <el-icon><Document /></el-icon>
                          文件管理
                        </el-dropdown-item>
                        <el-dropdown-item command="export">
                          <el-icon><Download /></el-icon>
                          导出知识库
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination" style="padding: 0 20px 20px 20px;">
        <el-pagination
          v-model:current-page="currentPageModel"
          v-model:page-size="pageSizeModel"
          :page-sizes="[10, 20, 50, 100, 200]"
          :total="displayTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="size => $emit('size-change', size)"
          @current-change="page => $emit('page-change', page)"
        />
      </div>
    </el-card>

</template>
<script setup>
import { computed } from 'vue'
import { Plus, Search, Document, ArrowDown, UploadFilled, View, Edit, Unlock, Lock, Check, Close, DocumentCopy, ArrowLeft, Download } from '@element-plus/icons-vue'
const props = defineProps({ userMode: Boolean, knowledgeBases: Array, loading: Boolean, searchKeyword: String, filterVectorStoreType: String, filterStatus: String, currentPage: Number, pageSize: Number, displayTotal: Number, getStatusText: Function, isActive: Function, getEmbeddingModelName: Function, getModelPlainStyle: Function, getVectorStoreTypeTag: Function, getVectorStoreInstanceName: Function, formatDate: Function })
const emit = defineEmits(['update:searchKeyword', 'update:filterVectorStoreType', 'update:filterStatus', 'update:currentPage', 'update:pageSize', 'search', 'filter', 'create', 'import', 'back', 'summary', 'delete', 'dropdown', 'size-change', 'page-change'])
const createModel = key => computed({ get: () => props[key], set: value => emit('update:' + key, value) })
const searchKeywordModel = createModel('searchKeyword')
const filterVectorStoreTypeModel = createModel('filterVectorStoreType')
const filterStatusModel = createModel('filterStatus')
const currentPageModel = createModel('currentPage')
const pageSizeModel = createModel('pageSize')
</script>
