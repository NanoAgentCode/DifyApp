import { computed } from 'vue'

const IMAGE_TYPES = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp']

export function useDocumentRendererAdapter(documentInfo) {
  const fileType = computed(() => {
    const fileName = documentInfo.value?.originalFileName || documentInfo.value?.fileName || ''
    return fileName.split('.').pop()?.toLowerCase() || ''
  })

  const renderer = computed(() => {
    if (fileType.value === 'pdf') return 'pdf'
    if (IMAGE_TYPES.includes(fileType.value)) return 'image'
    if (['md', 'markdown'].includes(fileType.value)) return 'markdown'
    if (fileType.value === 'txt') return 'text'
    if (['doc', 'docx'].includes(fileType.value)) return 'docx'
    return 'unsupported'
  })

  return { fileType, renderer, isImageType: computed(() => renderer.value === 'image') }
}
