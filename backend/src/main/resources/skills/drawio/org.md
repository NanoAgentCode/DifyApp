【图表类型特定要求】

- org（组织架构）必须使用模板：list-column-simple-vertical-arrow
- 仅支持扁平列表结构，不支持 children
- 通过 label 使用分隔符表示层级，例如：CEO - CTO - 技术部


【额外生成要求】

- 必须包含所有层级和人员，至少 3-4 层


【示例】
infographic list-column-simple-vertical-arrow
  design
    title "公司组织架构"
  data
    title "组织架构"
    items
      - label "CEO"
        desc "首席执行官"
      - label "CEO - CTO"
        desc "首席技术官"
      - label "CEO - CTO - 技术部"
        desc "技术开发部门"
      - label "CEO - CFO"
        desc "首席财务官"
