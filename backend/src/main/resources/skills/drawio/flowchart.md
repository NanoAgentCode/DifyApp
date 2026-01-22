【图表类型特定要求】

- flowchart（流程图）必须使用模板：list-row-simple-horizontal-arrow
- 支持扁平列表结构，不支持 children


【额外生成要求】

- 必须包含所有步骤、判断分支、循环等完整流程，至少 8-15 个步骤


【示例】
infographic list-row-simple-horizontal-arrow
  design
    title "用户登录流程"
  data
    title "登录步骤"
    items
      - label "打开登录页"
        desc "用户访问系统"
      - label "输入账号密码"
        desc "填写登录信息"
      - label "点击登录"
        desc "提交登录请求"
      - label "验证身份"
        desc "系统验证用户"
      - label "登录成功"
        desc "跳转到主页"
