<script setup lang="ts">
import { Folder, Document, Delete } from '@element-plus/icons-vue'

export interface TreeNode {
  name: string
  path: string
  type: 'file' | 'dir'
  children?: TreeNode[]
  file?: { id: number; path: string; sizeBytes: number | null }
}

defineProps<{
  nodes: TreeNode[]
  expandedDirs: Set<string>
  currentPath: string | null
  depth?: number
}>()

const emit = defineEmits<{
  click: [node: TreeNode]
  delete: [file: { id: number; path: string }]
}>()
</script>

<template>
  <div>
    <template v-for="node in nodes" :key="node.path">
      <div
        class="tree-item"
        :class="{ active: currentPath === node.path }"
        :style="{ paddingLeft: (depth || 0) * 16 + 16 + 'px' }"
        @click="emit('click', node)"
      >
        <!-- 展开/折叠箭头（仅目录） -->
        <span v-if="node.type === 'dir'" class="arrow" :class="{ expanded: expandedDirs.has(node.path) }">&#9654;</span>
        <span v-else class="arrow-placeholder"></span>
        <el-icon v-if="node.type === 'dir'" style="color: #909399"><Folder /></el-icon>
        <el-icon v-else style="color: #606266"><Document /></el-icon>
        <span class="node-name">{{ node.name }}</span>
        <el-button
          v-if="node.type === 'file' && node.file"
          link
          size="small"
          type="danger"
          class="delete-btn"
          @click.stop="emit('delete', node.file!)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <!-- 递归渲染子目录 -->
      <FileTreeNode
        v-if="node.type === 'dir' && expandedDirs.has(node.path) && node.children"
        :nodes="node.children"
        :expanded-dirs="expandedDirs"
        :current-path="currentPath"
        :depth="(depth || 0) + 1"
        @click="(n) => emit('click', n)"
        @delete="(f) => emit('delete', f)"
      />
    </template>
  </div>
</template>

<style scoped>
.tree-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
}

.tree-item:hover {
  background: #f5f7fa;
}

.tree-item.active {
  background: #ecf5ff;
  color: #409eff;
}

.arrow {
  font-size: 10px;
  width: 14px;
  text-align: center;
  color: #909399;
  transition: transform 0.15s;
  flex-shrink: 0;
}

.arrow.expanded {
  transform: rotate(90deg);
}

.arrow-placeholder {
  width: 14px;
  flex-shrink: 0;
}

.node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.delete-btn {
  opacity: 0;
  margin-left: auto;
}

.tree-item:hover .delete-btn {
  opacity: 1;
}
</style>
