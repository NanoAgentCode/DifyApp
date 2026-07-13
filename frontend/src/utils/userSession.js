const USER_INFO_KEY = 'userInfo'

export function getStoredUserInfo() {
  const raw = localStorage.getItem(USER_INFO_KEY)
  if (!raw) return null

  try {
    return JSON.parse(raw)
  } catch (error) {
    console.warn('解析用户信息失败，已清理损坏的会话数据', error)
    localStorage.removeItem(USER_INFO_KEY)
    return null
  }
}

export function getStoredUserId() {
  const userInfo = getStoredUserInfo()
  return userInfo?.userId ?? userInfo?.id ?? null
}

export function isAdminUser(userInfo = getStoredUserInfo()) {
  return userInfo?.role === 1 || userInfo?.role === 'ADMIN'
}

export function getUserHomePath(userInfo = getStoredUserInfo()) {
  return isAdminUser(userInfo) ? '/admin' : '/user'
}

export function setStoredUserInfo(userInfo) {
  if (userInfo) {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
  } else {
    localStorage.removeItem(USER_INFO_KEY)
  }
}

export function clearUserSession() {
  localStorage.removeItem('token')
  localStorage.removeItem(USER_INFO_KEY)
}
