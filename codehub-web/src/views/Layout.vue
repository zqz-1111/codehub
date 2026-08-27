<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'
import { logout } from '../api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isCollapse = ref(false)

const userInitial = computed(() => {
  const name = userStore.user?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const currentBreadcrumb = computed(() => {
  if (route.path === '/') return '工作台'
  if (route.path.startsWith('/repositories')) return '代码仓库'
  if (route.path.startsWith('/repos/')) return '仓库详情'
  if (route.path.startsWith('/admin')) return '管理后台'
  return ''
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出当前账号吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    try {
      await logout()
    } catch {
      // 忽略请求错误
    }
    userStore.clearUser()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '72px' : '240px'" class="aside">
      <!-- 品牌 Logo -->
      <div class="logo-box" @click="router.push('/')">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div v-if="!isCollapse" class="logo-text">
          <span class="brand-title">CodeHub</span>
          <span class="brand-badge">AI 2.0</span>
        </div>
      </div>

      <!-- 导航菜单 -->
      <el-menu
        :default-active="$route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="custom-menu"
      >
        <el-menu-item index="/">
          <el-icon><DataBoard /></el-icon>
          <template #title>
            <span class="menu-label">工作台</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/repositories">
          <el-icon><FolderOpened /></el-icon>
          <template #title>
            <span class="menu-label">代码仓库</span>
          </template>
        </el-menu-item>

        <el-menu-item v-if="userStore.isAdmin()" index="/admin">
          <el-icon><Management /></el-icon>
          <template #title>
            <span class="menu-label">管理治理</span>
          </template>
        </el-menu-item>
      </el-menu>

      <!-- 侧边栏底部 AI 状态卡片 -->
      <div v-if="!isCollapse" class="aside-footer">
        <div class="engine-badge">
          <span class="pulse-dot"></span>
          <div class="engine-info">
            <div class="engine-name">Qwen 3.7 Plus</div>
            <div class="engine-status">RAG 索引服务就绪</div>
          </div>
        </div>
      </div>
    </el-aside>

    <!-- 右侧容器 -->
    <el-container class="right-container">
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" @click="isCollapse = !isCollapse">
            <el-icon :size="18">
              <Fold v-if="!isCollapse" />
              <Expand v-else />
            </el-icon>
          </button>

          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentBreadcrumb">{{ currentBreadcrumb }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- 快捷链接 -->
          <a
            href="https://github.com/zqz-1111/codehub"
            target="_blank"
            class="header-action-btn"
            title="GitHub 仓库"
          >
            <el-icon><Platform /></el-icon>
          </a>

          <!-- 用户信息下拉 -->
          <el-dropdown trigger="click" class="user-dropdown">
            <div class="user-profile">
              <div class="avatar-badge">{{ userInitial }}</div>
              <div class="user-detail">
                <span class="user-name">{{ userStore.user?.username || '开发者' }}</span>
                <span :class="['role-pill', userStore.isAdmin() ? 'role-admin' : 'role-user']">
                  {{ userStore.user?.role || 'USER' }}
                </span>
              </div>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="user-menu-dropdown">
                <div class="dropdown-header">
                  <div class="header-username">{{ userStore.user?.username }}</div>
                  <div class="header-email">{{ userStore.user?.email || '未设置邮箱' }}</div>
                </div>
                <el-dropdown-item divided @click="router.push('/repositories')">
                  <el-icon><Folder /></el-icon>我的代码库
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin()" @click="router.push('/admin')">
                  <el-icon><Setting /></el-icon>系统控制台
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout" class="logout-item">
                  <el-icon><SwitchButton /></el-icon>退出账号
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主视图区域 -->
      <el-main class="main-content">
        <div class="view-wrapper">
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-app);
  overflow: hidden;
}

/* 侧边栏纯净质感 */
.aside {
  background-color: var(--bg-surface);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  z-index: 10;
  box-shadow: var(--shadow-xs);
}

.logo-box {
  height: 64px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-light);
}

.logo-icon {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: var(--radius-md);
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.3);
}

.logo-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-title {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.5px;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  background: #e0e7ff;
  color: var(--primary);
  border-radius: var(--radius-full);
}

/* 侧边栏导航菜单 */
.custom-menu {
  border-right: none;
  background: transparent;
  padding: 12px 10px;
  flex: 1;
}

.custom-menu :deep(.el-menu-item) {
  height: 46px;
  line-height: 46px;
  margin-bottom: 4px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-weight: 500;
  transition: all var(--transition-fast);
}

.custom-menu :deep(.el-menu-item:hover) {
  color: var(--primary);
  background-color: var(--bg-subtle);
}

.custom-menu :deep(.el-menu-item.is-active) {
  color: var(--primary);
  background: var(--primary-gradient-subtle);
  font-weight: 600;
}

.custom-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  background: var(--primary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.menu-label {
  font-size: 14px;
}

/* 侧边栏底部 */
.aside-footer {
  padding: 16px;
  border-top: 1px solid var(--border-light);
}

.engine-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-subtle);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background-color: var(--success);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
  animation: pulseGlow 2s infinite;
}

@keyframes pulseGlow {
  0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4); }
  70% { box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
  100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
}

.engine-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-main);
}

.engine-status {
  font-size: 11px;
  color: var(--text-muted);
}

/* 顶栏 */
.right-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--bg-glass);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color);
  z-index: 9;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  background: none;
  border: none;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.collapse-btn:hover {
  background-color: var(--bg-subtle);
  color: var(--primary);
}

.breadcrumb {
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-action-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  text-decoration: none;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  transition: all var(--transition-fast);
}

.header-action-btn:hover {
  color: var(--primary);
  border-color: var(--primary);
  background-color: var(--primary-light);
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 4px 4px;
  border-radius: var(--radius-full);
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  transition: all var(--transition-fast);
}

.user-profile:hover {
  border-color: var(--border-hover);
  box-shadow: var(--shadow-sm);
}

.avatar-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 2px 6px rgba(79, 70, 229, 0.25);
}

.user-detail {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.role-pill {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 4px;
  width: fit-content;
}

.role-admin {
  background-color: #fef2f2;
  color: #dc2626;
}

.role-user {
  background-color: #eff6ff;
  color: #2563eb;
}

.dropdown-arrow {
  color: var(--text-muted);
  font-size: 12px;
}

.user-menu-dropdown {
  min-width: 200px;
  border-radius: var(--radius-lg);
  padding: 6px;
}

.dropdown-header {
  padding: 10px 14px 8px;
}

.header-username {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-main);
}

.header-email {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.logout-item {
  color: var(--danger) !important;
}

/* 主内容 */
.main-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background-color: var(--bg-app);
}

.view-wrapper {
  max-width: 1400px;
  margin: 0 auto;
}

/* 页面切换动画 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
