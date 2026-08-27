import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('../views/Register.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'Dashboard',
          component: () => import('../views/Dashboard.vue'),
        },
        {
          path: 'repositories',
          name: 'Repositories',
          component: () => import('../views/Repositories.vue'),
        },
        {
          path: 'repos/:id',
          name: 'RepoDetail',
          component: () => import('../views/RepoDetail.vue'),
        },
        {
          path: 'admin',
          name: 'Admin',
          component: () => import('../views/Admin.vue'),
          meta: { requiresAdmin: true },
        },
      ],
    },
  ],
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  // 不需要认证的页面直接放行
  if (to.meta.requiresAuth === false) {
    // 已登录用户访问登录页，跳转首页
    if (userStore.isLoggedIn()) {
      next('/')
    } else {
      next()
    }
    return
  }

  // 需要认证但未登录
  if (!userStore.isLoggedIn()) {
    next('/login')
    return
  }

  // 需要管理员权限
  if (to.meta.requiresAdmin && !userStore.isAdmin()) {
    next('/')
    return
  }

  next()
})

export default router
