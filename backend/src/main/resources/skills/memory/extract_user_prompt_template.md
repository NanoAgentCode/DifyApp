{
  "question": {questionJson},
  "answer": {answerJson}
}

请输出JSON，结构如下：
{
  "long_term_facts": [
    {"key": "", "content": "", "importance": 0}
  ],
  "entities": [
    {"type": "", "name": "", "attributes": {}}
  ]
}
约束：importance 取 0-5；key 应短且稳定，同一偏好/习惯维度使用同一 key；entities 的 attributes 只能是对象。
