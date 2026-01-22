// 空模块 - 用于替换在浏览器中不需要的 Node.js 模块
// 这个文件用于 source-map-js 等模块的浏览器兼容性处理

export const SourceMapConsumer = class {
  constructor() {}
  static fromSourceMap() { return new SourceMapConsumer() }
  static with() { return Promise.resolve() }
  destroy() {}
  eachMapping() {}
  originalPositionFor() { return { source: null, line: null, column: null, name: null } }
  generatedPositionFor() { return { line: null, column: null, lastColumn: null } }
  sourceContentFor() { return null }
  hasContentsOfAllSources() { return false }
  computeColumnSpans() {}
}

export const SourceMapGenerator = class {
  constructor() {
    this._mappings = []
    this._sources = []
    this._names = []
    this._file = ''
    this._sourceRoot = ''
  }
  static fromSourceMap() { return new SourceMapGenerator() }
  addMapping() {}
  setSourceContent() {}
  applySourceMap() {}
  toString() { return '{"version":3,"sources":[],"names":[],"mappings":""}' }
  toJSON() { return { version: 3, sources: [], names: [], mappings: '' } }
}

export default {
  SourceMapConsumer,
  SourceMapGenerator,
}
