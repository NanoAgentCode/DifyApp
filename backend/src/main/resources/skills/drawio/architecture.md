【图表类型特定要求】

- architecture（架构图）必须使用模板：list-column-simple-vertical-arrow
- 仅支持扁平列表结构，不支持 children

【额外生成要求】

- 必须包含所有层级和组件，每层至少 3-5 个组件，至少 3-5 层

【示例】
infographic list-column-simple-vertical-arrow
  design
    title "微服务架构"
  data
    title "核心组件"
    items
      - label "API网关"
        desc "统一入口与鉴权"
      - label "用户服务"
        desc "账号与权限管理"
      - label "订单服务"
        desc "订单创建与状态流转"
      - label "支付服务"
        desc "支付与退款处理"
      - label "数据库层"
        desc "用户、订单与支付数据存储"
