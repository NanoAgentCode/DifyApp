【图表类型特定要求】

- network（网络图）必须使用模板：list-row-simple-horizontal-arrow
- 支持扁平列表结构，不支持 children


【额外生成要求】

- 必须包含所有网络设备和连接，至少 8-12 个设备节点


【示例】
infographic list-row-simple-horizontal-arrow
  design
    title "企业网络拓扑"
  data
    title "网络设备"
    items
      - label "互联网出口"
        desc "运营商链路"
      - label "防火墙"
        desc "安全策略与访问控制"
      - label "核心交换机"
        desc "汇聚与路由"
      - label "接入交换机"
        desc "终端接入"
      - label "业务服务器"
        desc "应用服务集群"
