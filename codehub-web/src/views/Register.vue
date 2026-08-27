<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

const router = useRouter()

const form = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
})

const loading = ref(false)

async function handleRegister() {
  if (!form.value.username.trim() || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.warning('密码长度至少6个字符')
    return
  }

  loading.value = true
  try {
    await register({
      username: form.value.username.trim(),
      password: form.value.password,
      email: form.value.email ? form.value.email.trim() : undefined,
    })
    ElMessage.success('注册成功，欢迎加入 CodeHub！请登录')
    router.push('/login')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="bg-glow bg-glow-1"></div>
    <div class="bg-glow bg-glow-2"></div>

    <div class="auth-card animate-slide-up">
      <!-- 左侧特性介绍 -->
      <div class="auth-brand-side">
        <div class="brand-header">
          <div class="brand-logo">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 17L12 22L22 17" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="brand-name">CodeHub</span>
        </div>

        <div class="brand-content">
          <h1 class="hero-title">创建您的开发者账号</h1>
          <p class="hero-subtitle">加入高可靠性智能代码协同平台，立即体验 AI 代码语义问答</p>

          <div class="feature-list">
            <div class="feature-item">
              <div class="feature-icon"><el-icon><Key /></el-icon></div>
              <div class="feature-text">
                <div class="feature-heading">JWT + Redis 黑名单鉴权</div>
                <div class="feature-desc">无状态身份验证与原子登出安全管控</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon"><el-icon><FolderChecked /></el-icon></div>
              <div class="feature-text">
                <div class="feature-heading">细粒度 RBAC 权限体系</div>
                <div class="feature-desc">OWNER / WRITE / READ 三级安全仓库访问控制</div>
              </div>
            </div>

            <div class="feature-item">
              <div class="feature-icon"><el-icon><Cpu /></el-icon></div>
              <div class="feature-text">
                <div class="feature-heading">企业级可靠性保障</div>
                <div class="feature-desc">@Idempotent 接口防重 + Redisson 分布式锁</div>
              </div>
            </div>
          </div>
        </div>

        <div class="brand-footer">
          <span class="version-tag">Secure & Scalable Architecture</span>
        </div>
      </div>

      <!-- 右侧注册表单 -->
      <div class="auth-form-side">
        <div class="form-header">
          <h2 class="form-title">注册新账号</h2>
          <p class="form-desc">请填写基础信息完成开发者入驻</p>
        </div>

        <el-form :model="form" class="auth-form" @keyup.enter="handleRegister">
          <el-form-item>
            <div class="input-label">用户名</div>
            <el-input
              v-model="form.username"
              placeholder="3-20位字符（字母/数字/下划线）"
              prefix-icon="User"
              size="large"
              class="custom-input"
            />
          </el-form-item>

          <el-form-item>
            <div class="input-label">电子邮箱（选填）</div>
            <el-input
              v-model="form.email"
              placeholder="name@example.com"
              prefix-icon="Message"
              size="large"
              class="custom-input"
            />
          </el-form-item>

          <el-form-item>
            <div class="input-label">密码</div>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="不少于6位密码"
              prefix-icon="Lock"
              size="large"
              show-password
              class="custom-input"
            />
          </el-form-item>

          <el-form-item>
            <div class="input-label">确认密码</div>
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再次输入密码确认"
              prefix-icon="Lock"
              size="large"
              show-password
              class="custom-input"
            />
          </el-form-item>

          <el-form-item style="margin-top: 10px">
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="submit-btn"
              @click="handleRegister"
            >
              创建账号
            </el-button>
          </el-form-item>
        </el-form>

        <div class="form-footer">
          <span>已有开发者账号？</span>
          <router-link to="/login" class="link-btn">返回直接登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8fafc;
  position: relative;
  overflow: hidden;
  padding: 20px;
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
  opacity: 0.6;
}

.bg-glow-1 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.15) 0%, rgba(59, 130, 246, 0.05) 70%);
  top: -100px;
  left: -100px;
}

.bg-glow-2 {
  width: 450px;
  height: 450px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.15) 0%, rgba(16, 185, 129, 0.05) 70%);
  bottom: -100px;
  right: -100px;
}

.auth-card {
  width: 960px;
  max-width: 100%;
  min-height: 600px;
  background: var(--bg-surface);
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-lg);
  display: flex;
  overflow: hidden;
  z-index: 1;
}

.auth-brand-side {
  flex: 1.1;
  background: linear-gradient(145deg, #f8fafc 0%, #eef2ff 100%);
  border-right: 1px solid var(--border-color);
  padding: 40px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.brand-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
}

.brand-name {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.5px;
}

.brand-content {
  margin: 20px 0;
}

.hero-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-main);
  line-height: 1.35;
  margin-bottom: 10px;
}

.hero-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 24px;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(6px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: var(--radius-md);
}

.feature-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  margin-top: 2px;
}

.feature-heading {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-main);
}

.feature-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 1px;
}

.brand-footer {
  font-size: 11px;
}

.version-tag {
  font-weight: 600;
  color: var(--primary);
  background: #e0e7ff;
  padding: 3px 8px;
  border-radius: var(--radius-full);
}

.auth-form-side {
  flex: 1;
  padding: 36px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: var(--bg-surface);
}

.form-header {
  margin-bottom: 20px;
}

.form-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
}

.form-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
}

.input-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.custom-input {
  width: 100%;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  background: var(--primary-gradient) !important;
  border: none !important;
  border-radius: var(--radius-md);
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3) !important;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.4) !important;
}

.form-footer {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}

.link-btn {
  color: var(--primary);
  font-weight: 600;
  text-decoration: none;
  margin-left: 6px;
  transition: all var(--transition-fast);
}

.link-btn:hover {
  text-decoration: underline;
  color: var(--primary-hover);
}

@media (max-width: 820px) {
  .auth-brand-side {
    display: none;
  }
  .auth-card {
    width: 440px;
  }
}
</style>
