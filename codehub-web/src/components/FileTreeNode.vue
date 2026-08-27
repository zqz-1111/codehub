<script setup lang="ts">
import { Folder, Document, Delete } from '@element-plus/icons-vue'

export interface TreeNode {
  name: string
  path: string
  type: 'file' | 'dir'
  children?: TreeNode[]
  file?: { id: number; path: string; sizeBytes: number | null }
}

const props = defineProps<{
  nodes: TreeNode[]
  expandedDirs: Set<string>
  currentPath: string | null
  depth?: number
}>()

const emit = defineEmits<{
  click: [node: TreeNode]
  delete: [file: { id: number; path: string }]
}>()

// 根据文件名获取图标颜色与标识
function getFileTag(name: string) {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  switch (ext) {
    case 'java': return { color: '#ef4444', label: 'J' }
    case 'ts': return { color: '#3b82f6', label: 'TS' }
    case 'js': return { color: '#f59e0b', label: 'JS' }
    case 'vue': return { color: '#10b981', label: 'V' }
    case 'py': return { color: '#06b6d4', label: 'PY' }
    case 'json': return { color: '#8b5cf6', label: '{ }' }
    case 'md': return { color: '#0284c7', label: 'M↓' }
    case 'sql': return { color: '#ec4899', label: 'SQL' }
    case 'xml': case 'yml': case 'yaml': return { color: '#64748b', label: '⚙' }
    default: return { color: '#94a3b8', label: '' }
  }
}
</script>

<template>
  <div class="tree-nodes-container">
    <template v-for="node in nodes" :key="node.path">
      <div
        class="tree-row"
        :class="{ 'is-active': currentPath === node.path }"
        :style="{ paddingLeft: (depth || 0) * 16 + 12 + 'px' }"
        @click="emit('click', node)"
      >
        <!-- 展开/折叠三角图标 -->
        <span
          v-if="node.type === 'dir'"
          class="dir-arrow"
          :class="{ 'is-expanded': expandedDirs.has(node.path) }"
        >
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5">
            <polyline points="9 18 15 12 9 6"></polyline>
          </svg>
        </span>
        <span v-else class="dir-arrow-spacer"></span>

        <!-- 节点图标 -->
        <div v-if="node.type === 'dir'" class="node-icon icon-dir">
          <el-icon><Folder /></el-icon>
        </div>
        <div
          v-else
          class="node-icon icon-file-custom"
          :style="{ color: getFileTag(node.name).color, borderColor: getFileTag(node.name).color + '40' }"
        >
          <span v-if="getFileTag(node.name).label" class="file-ext-tag">{{ getFileTag(node.name).label }}</span>
          <el-icon v-else><Document /></el-icon>
        </div>

        <!-- 节点名称 -->
        <span class="node-title">{{ node.name }}</span>

        <!-- 删除按钮 -->
        <button
          v-if="node.type === 'file' && node.file"
          class="row-delete-btn"
          title="删除文件"
          @click.stop="emit('delete', node.file!)"
        >
          <el-icon><Delete /></el-icon>
        </button>
      </div>

      <!-- 递归子节点 -->
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
.tree-nodes-container {
  display: flex;
  flex-direction: column;
}

.tree-row {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding-right: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-regular);
  border-radius: var(--radius-sm);
  margin: 1px 4px;
  transition: all var(--transition-fast);
  user-select: none;
}

.tree-row:hover {
  background-color: var(--bg-subtle);
  color: var(--text-main);
}

.tree-row.is-active {
  background-color: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
}

.dir-arrow {
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: transform var(--transition-fast);
  flex-shrink: 0;
}

.dir-arrow.is-expanded {
  transform: rotate(90deg);
}

.dir-arrow-spacer {
  width: 14px;
  flex-shrink: 0;
}

.node-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.icon-dir {
  color: #3b82f6;
}

.icon-file-custom {
  font-size: 10px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
}

.file-ext-tag {
  font-size: 9px;
  line-height: 1;
}

.node-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-delete-btn {
  opacity: 0;
  border: none;
  background: transparent;
  color: var(--danger);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: all var(--transition-fast);
}

.tree-row:hover .row-delete-btn {
  opacity: 0.8;
}

.row-delete-btn:hover {
  opacity: 1 !important;
  background-color: var(--danger-light);
}
</style>
