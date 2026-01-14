import { useRouter } from 'vue-router'

export function useAppNavigation() {
  const router = useRouter()

  const navigateToApp = (app) => {
    if (app.type === 1) {
      router.push(`/app/chat/${app.id}`)
    } else {
      router.push(`/app/workflow/${app.id}`)
    }
  }

  const navigateToAppById = (id, type) => {
    if (type === 1) {
      router.push(`/app/chat/${id}`)
    } else {
      router.push(`/app/workflow/${id}`)
    }
  }

  return {
    navigateToApp,
    navigateToAppById
  }
}
