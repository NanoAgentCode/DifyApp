【图表类型特定要求】

- uml（UML图）必须使用模板：list-column-simple-vertical-arrow
- 仅支持扁平列表结构，不支持 children
- 通过 label 使用分隔符表示层级与关系，例如：User - Order


【额外生成要求】

- 必须包含所有相关的类和关系，至少 5-8 个类


【示例】
infographic list-column-simple-vertical-arrow
  design
    title "电商系统UML类图"
  data
    title "类及其关系"
    items
      - label "User（用户类）"
        desc "用户基本信息：id, name, email"
      - label "Product（商品类）"
        desc "商品信息：id, name, price, stock"
      - label "Order（订单类）"
        desc "订单信息：id, userId, totalAmount, status"
      - label "OrderItem（订单项类）"
        desc "订单明细：orderId, productId, quantity, price"
      - label "User - Order"
        desc "一对多关系：一个用户可以有多个订单"
