let infographicClass = null

export async function loadInfographicEditor() {
  if (infographicClass) return infographicClass

  const module = await import('@antv/infographic')
  infographicClass = module.Infographic || module.default?.Infographic || module.default
  if (!infographicClass) {
    throw new Error('无法加载 @antv/infographic 编辑器')
  }
  return infographicClass
}
