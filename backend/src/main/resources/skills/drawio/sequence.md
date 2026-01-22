【图表类型特定要求】

- sequence（时序图）必须使用模板：sequence-zigzag-steps-underline-text
- 支持扁平列表结构，不支持 children

【额外生成要求】

- 必须包含所有参与者和完整的交互序列，至少 4-6 个参与者，10-15 条消息

【示例】
infographic sequence-zigzag-steps-underline-text
  design
    title "用户下单时序"
  data
    title "交互步骤"
    items
      - label "用户发起下单"
        desc "提交商品与收货信息"
      - label "前端请求订单服务"
        desc "调用创建订单接口"
      - label "订单服务创建订单"
        desc "生成订单号并记录状态"
      - label "订单服务请求支付服务"
        desc "生成支付单"
      - label "支付服务通知结果"
        desc "支付成功或失败回调"
