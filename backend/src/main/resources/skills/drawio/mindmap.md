【图表类型特定要求】

- mindmap（思维导图）必须使用模板：hierarchy-mindmap-branch-gradient-rounded-rect
- 支持 children 层级结构


【额外生成要求】

- 必须包含所有主要分支和子分支，至少 3-4 个主要分支，每个分支至少 2-3 个子分支


【示例】
infographic hierarchy-mindmap-branch-gradient-rounded-rect
  design
    title "项目管理思维导图"
  data
    title "项目管理"
    items
      - label "需求分析"
        desc "收集和分析需求"
        children
          - label "用户调研"
            desc "通过问卷、访谈等方式获取用户需求"
          - label "需求文档"
            desc "编写PRD产品需求文档"
      - label "设计"
        desc "系统设计和UI设计"
        children
          - label "架构设计"
            desc "设计系统整体架构和技术方案"
          - label "UI设计"
            desc "制作界面原型和视觉设计"
      - label "开发"
        desc "编码实现系统功能"
      - label "测试"
        desc "功能测试和性能测试"
      - label "部署"
        desc "上线部署到生产环境"
