import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '../api/auth'

export const useUserStore = defineStore('user', () => {
  const storedToken = localStorage.getItem('token')
  const storedUser = localStorage.getItem('user')
  const token = ref<string>(storedToken && storedToken !== 'undefined' ? storedToken : '')
  const user = ref<UserInfo | null>(
    storedUser && storedUser !== 'undefined' ? JSON.parse(storedUser) : null
  )

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUser(newUser: UserInfo) {
    user.value = newUser
    localStorage.setItem('user', JSON.stringify(newUser))
  }

  function clearUser() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  const isLoggedIn = () => !!token.value
  const isAdmin = () => user.value?.role === 'ADMIN'

  return { token, user, setToken, setUser, clearUser, isLoggedIn, isAdmin }
})
