export function setValueByPath(target, path, value) {
  if (!path || typeof path !== 'string') return target
  const parts = path.split('.').filter(Boolean)
  if (parts.length === 0) return target

  let cur = target
  for (let i = 0; i < parts.length - 1; i++) {
    const key = parts[i]
    if (cur[key] == null || typeof cur[key] !== 'object' || Array.isArray(cur[key])) {
      cur[key] = {}
    }
    cur = cur[key]
  }
  cur[parts[parts.length - 1]] = value
  return target
}

export function buildMappedInputs(fields, inputs, inputsJson) {
  const result = {}
  const list = Array.isArray(fields) ? fields : []

  for (const field of list) {
    const fromKey = field?.key
    if (!fromKey) continue
    const toKey = field?.mapTo || fromKey
    const value = inputs?.[fromKey]

    if (field?.type === 'json') {
      const jsonStr = inputsJson?.[fromKey]
      if (jsonStr && String(jsonStr).trim()) {
        try {
          setValueByPath(result, toKey, JSON.parse(jsonStr))
        } catch (e) {
          setValueByPath(result, toKey, value)
        }
      } else if (value !== null && value !== undefined && value !== '') {
        setValueByPath(result, toKey, value)
      }
      continue
    }

    if (field?.type === 'switch') {
      setValueByPath(result, toKey, value === true || value === 'true')
      continue
    }

    if (field?.type === 'number') {
      if (value !== null && value !== undefined && value !== '') {
        setValueByPath(result, toKey, Number(value))
      }
      continue
    }

    if (value !== null && value !== undefined && value !== '') {
      setValueByPath(result, toKey, value)
    }
  }

  return result
}

