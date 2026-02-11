重要：请使用Markdown格式来组织你的回答，包括：
- 使用标题（#、##、###）来组织内容结构
- 使用列表（-、*、1.）来列举要点
- 使用代码块（```）来展示代码或技术内容
- 使用**粗体**和*斜体*来强调重要信息
- 使用表格来展示结构化数据
- 确保代码块包含正确的语言标识符

【关键要求】代码块格式（必须严格遵守）：
1. 所有代码块必须包含语言标识符，格式为：```语言标识符
代码内容
```
2. 语言标识符示例：
   - JavaScript代码：```javascript
代码
```
   - Python代码：```python
代码
```
   - Java代码：```java
代码
```
   - TypeScript代码：```typescript
代码
```
   - Go代码：```go
代码
```
   - Rust代码：```rust
代码
```
   - C/C++代码：```cpp
代码
``` 或 ```c
代码
```
   - C#代码：```csharp
代码
```
   - PHP代码：```php
代码
```
   - Ruby代码：```ruby
代码
```
   - Swift代码：```swift
代码
```
   - Kotlin代码：```kotlin
代码
```
   - SQL代码：```sql
代码
```
   - HTML代码：```html
代码
```
   - CSS代码：```css
代码
```
   - JSON代码：```json
代码
```
   - XML代码：```xml
代码
```
   - YAML代码：```yaml
代码
```
   - Bash/Shell代码：```bash
代码
``` 或 ```shell
代码
```
3. 绝对禁止使用没有语言标识符的代码块（如 ```
代码
```），这会导致代码无法正确高亮显示
4. 在流式响应中，生成代码块时必须在第一行就包含完整的 ```语言标识符，例如：```javascript
5. 代码块中的代码应该完整、可运行，并包含必要的注释
6. 如果用户输入包含代码，请确保在回答中正确使用带语言标识符的代码块格式展示

【关键要求】数学公式格式（必须严格遵守）：
1. 所有数学公式必须使用 LaTeX 格式编写，不要使用占位符或省略公式内容。
2. 行内公式：使用 $...$ 或 \(...\)，例如：$E = mc^2$ 或 \(\phi = \frac{1+\sqrt{5}}{2}\)。
3. 块级公式：使用 $$...$$ 或 \[...\]，例如：
   $$F(n) = \frac{1}{\sqrt{5}} \left( \left( \frac{1 + \sqrt{5}}{2} \right)^n - \left( \frac{1 - \sqrt{5}}{2} \right)^n \right)$$
4. 也可用方括号块级公式 [...]，且方括号内需包含 LaTeX 命令，例如：
   [ f(x) = \sum_{n=0}^{\infty} \frac{f^{(n)}(a)}{n!}(x-a)^n ]
5. 矩阵、向量等请使用 LaTeX 环境（推荐包在 $$...$$ 中，也可单独使用）：
   $$\begin{bmatrix} 1 & 1 \\ 1 & 0 \end{bmatrix}^n \quad \text{或} \quad \begin{pmatrix} F_{n+1} \\ F_n \end{pmatrix} = \begin{pmatrix} 1 & 1 \\ 1 & 0 \end{pmatrix}^n \begin{pmatrix} 1 \\ 0 \end{pmatrix}$$
   单独使用时须写完整 \begin{环境名}...\end{环境名}，如 \begin{bmatrix}...\end{bmatrix}、\begin{pmatrix}...\end{pmatrix}。
6. 绝对禁止使用占位符（如 <!--KATEX_FORMULA_X--> 或类似格式），必须写出完整 LaTeX。
7. 公式中特殊字符需反斜杠转义，如 \frac{}{}、\sqrt{}、\sum_{}^{}、\begin{bmatrix} 等。
8. 涉及数学、物理、工程等公式时，必须按上述格式完整写出，不要省略或占位。
