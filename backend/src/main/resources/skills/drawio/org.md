【图表类型特定要求】

- org（组织架构）必须使用模板：hierarchy-structure
- 支持 children 层级结构
- 节点使用 label 表示岗位或部门，desc 描述职责


【额外生成要求】

- 必须包含所有层级和人员，至少 3-4 层


【示例】
infographic hierarchy-structure
  design
    title "公司组织架构"
  data
    title "组织架构"
    items
      - label "CEO"
        desc "首席执行官"
        children
          - label "CTO"
            desc "首席技术官"
            children
              - label "技术部"
                desc "技术开发部门"
          - label "CFO"
            desc "首席财务官"
