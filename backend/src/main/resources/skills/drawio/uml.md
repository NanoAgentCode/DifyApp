【图表类型特定要求】

- uml（UML图）必须使用模板：relation-dagre-flow
- 支持 children 层级结构，节点之间可用关系边表达
- 节点使用 label 表示类名，desc 描述属性或职责


【额外生成要求】

- 必须包含所有相关的类和关系，至少 5-8 个类


【示例】
infographic relation-dagre-flow
  design
    title "电商系统UML类图"
  data
    title "类及其关系"
    items
      - label "User"
        desc "用户：id, name, email"
      - label "Product"
        desc "商品：id, name, price, stock"
      - label "Order"
        desc "订单：id, userId, totalAmount, status"
      - label "OrderItem"
        desc "订单项：orderId, productId, quantity, price"
      - label "Cart"
        desc "购物车：userId, productId, quantity"
      - label "User -> Order"
        desc "一对多：用户拥有多个订单"
      - label "Order -> OrderItem"
        desc "一对多：订单包含多个订单项"
      - label "Product -> OrderItem"
        desc "一对多：商品被多个订单项引用"
