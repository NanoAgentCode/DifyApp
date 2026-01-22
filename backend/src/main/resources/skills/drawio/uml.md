【图表类型特定要求】

- uml（UML图）必须使用模板：relation-dagre-flow-lr-badge-card
- 节点使用 items 列表，必须包含 id、label、desc
- 关系必须放在 data.relations 中，用 from/to/label 表达，不要把 “A -> B” 写进 items
- desc 用中文短语，属性之间用 “、” 分隔，不要出现 \n 或多行
- label/desc 内容不要再额外包裹引号


【额外生成要求】

- 必须包含所有相关的类和关系，至少 5-8 个类


【示例】
infographic relation-dagre-flow-lr-badge-card
  design
    title "电商系统UML类图"
  data
    title "类及其关系"
    items
      - id "User"
        label "用户"
        desc "id, name, email"
      - id "Product"
        label "商品"
        desc "id, name, price, stock"
      - id "Order"
        label "订单"
        desc "id, userId, totalAmount, status"
      - id "OrderItem"
        label "订单项"
        desc "orderId, productId, quantity, price"
      - id "Cart"
        label "购物车"
        desc "userId, productId, quantity"
    relations
      - from "User"
        to "Order"
        label "一对多"
      - from "Order"
        to "OrderItem"
        label "一对多"
      - from "Product"
        to "OrderItem"
        label "一对多"
