# Windows 客户端打包指南

本文档说明如何将前端应用打包成 Windows 桌面客户端（使用 Tauri）。

## 前置要求

### 1. 安装 Rust

Tauri 需要 Rust 环境。请访问 [Rust 官网](https://www.rust-lang.org/) 安装 Rust。

**Windows 用户：**
```bash
# 下载并运行 rustup-init.exe
# 或使用 PowerShell
Invoke-WebRequest -Uri https://win.rustup.rs/x86_64 -OutFile rustup-init.exe
.\rustup-init.exe
```

安装完成后，重启终端并验证：
```bash
rustc --version
cargo --version
```

### 2. 系统依赖

**Windows：**
- 需要安装 Microsoft Visual C++ Build Tools
- 下载地址：https://visualstudio.microsoft.com/visual-cpp-build-tools/

### 3. 安装 Node.js 和包管理器

确保已安装 Node.js 16+ 和 npm/yarn。

## 打包步骤

### 1. 安装依赖

```bash
cd frontend
npm install
# 或
yarn install
```

### 2. 配置后端 API 地址

在打包前，需要配置后端 API 地址。有两种方式：

**方式一：使用环境变量（推荐）**

在 `frontend` 目录下创建 `.env` 文件：

```env
# 后端 API 地址
# 例如：http://localhost:8081 或 http://192.168.1.100:8081
VITE_API_BASE_URL=http://localhost:8081
```

**方式二：直接修改配置文件**

编辑 `frontend/src/config/api.js`，修改 `defaultBaseURL` 的值：

```javascript
const defaultBaseURL = 'http://your-backend-ip:port'
```

### 3. 构建前端

```bash
npm run build
# 或
yarn build
```

### 4. 打包客户端

**开发模式（测试）：**
```bash
npm run tauri:dev
# 或
yarn tauri:dev
```

**生产模式（打包）：**
```bash
npm run tauri:build
# 或
yarn tauri:build
```

打包完成后，安装包位于：
- **NSIS 安装包**：`src-tauri/target/release/bundle/nsis/dify-app_0.1.0_x64-setup.exe`
- **可执行文件**：`src-tauri/target/release/app.exe`

**说明：**
- 默认配置使用 NSIS 安装包，打包时会自动下载 NSIS 工具
- 如果遇到 NSIS 下载超时，可以：
  1. 直接使用已编译的 exe 文件：`src-tauri/target/release/app.exe`
  2. 或禁用安装包生成（见常见问题 4）
  3. 或手动下载 NSIS 工具（见常见问题 4）

### 5. 打包调试版本

```bash
npm run tauri:build -- --debug
```

## 配置说明

### 窗口配置

窗口配置在 `src-tauri/tauri.conf.json` 中：

```json
{
  "app": {
    "windows": [
      {
        "title": "智能工作台",
        "width": 1024,
        "height": 768,
        "minWidth": 800,
        "minHeight": 600,
        "resizable": true,
        "fullscreen": false,
        "center": true
      }
    ]
  }
}
```

### 应用信息

可以修改以下信息：
- `productName`: 应用名称
- `version`: 版本号
- `identifier`: 应用标识符（用于 Windows）

### 图标

应用图标位于 `src-tauri/icons/` 目录。如需更换图标，请替换该目录下的图标文件。

## 常见问题

### 1. Rust 编译错误

如果遇到 Rust 编译错误，尝试：
```bash
# 更新 Rust
rustup update

# 清理构建缓存
cd src-tauri
cargo clean
cd ..
```

### 2. 网络问题（下载依赖失败）

如果在中国大陆，可能需要配置 Rust 镜像源。创建或编辑 `%USERPROFILE%\.cargo\config`（Windows）：

```toml
[source.crates-io]
replace-with = 'ustc'

[source.ustc]
registry = "https://mirrors.ustc.edu.cn/crates.io-index"
```

### 3. 打包文件过大

Tauri 打包的文件已经比较小了。如果仍然觉得大，可以：
- 检查是否有不必要的依赖
- 使用代码分割（已在 vite.config.js 中配置）
- 压缩资源文件

### 4. NSIS 工具下载超时

在打包 NSIS 安装包时，Tauri 需要下载 NSIS 工具和 `nsis_tauri_utils.dll`。如果下载超时（错误：`failed to bundle project 'timeout: global'`），有以下解决方案：

**方案一：跳过安装包生成，直接使用 exe 文件（最简单，推荐）**

如果只需要 exe 文件，可以禁用安装包生成：

1. 修改 `src-tauri/tauri.conf.json`，将 `bundle.active` 设置为 `false`：
   ```json
   {
     "bundle": {
       "active": false,
       ...
     }
   }
   ```

2. 重新打包：
   ```bash
   npm run tauri:build
   ```

3. 编译后的 exe 文件位于：
   ```
   src-tauri/target/release/app.exe
   ```

**方案二：手动下载 NSIS 工具**

1. 手动下载 NSIS 工具：
   - NSIS 3.11：https://github.com/tauri-apps/binary-releases/releases/download/nsis-3.11/nsis-3.11.zip
   - nsis_tauri_utils.dll：https://github.com/tauri-apps/nsis-tauri-utils/releases/download/nsis_tauri_utils-v0.5.2/nsis_tauri_utils.dll

2. 解压 NSIS 到某个目录（如 `C:\nsis`）

3. 将 `nsis_tauri_utils.dll` 放到 NSIS 目录的 `Plugins\x86-unicode` 子目录中

4. 设置环境变量：
   ```powershell
   # 设置 NSIS 路径
   $env:NSIS = "C:\nsis"
   $env:PATH = "$env:NSIS;$env:PATH"
   ```

5. 重新打包：
   ```bash
   npm run tauri:build
   ```

**方案三：配置代理或使用镜像**

如果网络访问 GitHub 较慢，可以：

1. 配置代理（如果已有代理）：
   ```powershell
   $env:HTTP_PROXY = "http://your-proxy:port"
   $env:HTTPS_PROXY = "http://your-proxy:port"
   ```

2. 或使用网络加速工具（如使用 VPN 或加速器）

3. 重新打包：
   ```bash
   npm run tauri:build
   ```

**方案四：多次重试**

网络问题可能是暂时的，可以多次尝试打包命令：
```bash
npm run tauri:build
```

### 5. WiX 工具集下载超时（如果使用 MSI 打包）

如果配置了 MSI 打包，Tauri 需要下载 WiX 工具集。如果下载超时，有以下解决方案：

**方案一：只使用 NSIS 安装包（推荐，已默认配置）**

当前配置已设置为只使用 NSIS，无需额外下载工具。

**方案二：手动安装 WiX 工具集**

1. 下载 WiX 工具集：
   - 访问：https://github.com/wixtoolset/wix3/releases
   - 下载 `wix314-binaries.zip`
   - 解压到某个目录（如 `C:\wix`）

2. 设置环境变量：
   ```powershell
   # 设置 WiX 工具集路径
   $env:WIX = "C:\wix"
   $env:PATH = "$env:WIX;$env:PATH"
   ```

3. 修改配置启用 MSI：
   编辑 `src-tauri/tauri.conf.json`，将 `targets` 改为 `["msi", "nsis"]` 或 `["msi"]`

4. 重新打包：
   ```bash
   npm run tauri:build
   ```

**方案三：使用已编译的 exe 文件**

如果只需要 exe 文件，可以跳过安装包生成。编译后的 exe 位于：
```
src-tauri/target/release/app.exe
```

### 6. 后端连接失败

确保：
- 后端 API 地址配置正确
- 后端服务正在运行
- 防火墙允许连接
- 如果是远程服务器，确保服务器地址可访问

## 分发

打包后会生成 NSIS 安装包（`.exe` 文件），可以直接分发给用户安装。

安装包位置：`src-tauri/target/release/bundle/nsis/dify-app_0.1.0_x64-setup.exe`

## 更新应用

更新应用版本：
1. 修改 `package.json` 中的 `version`
2. 修改 `src-tauri/Cargo.toml` 中的 `version`
3. 修改 `src-tauri/tauri.conf.json` 中的 `version`
4. 重新打包

## 更多信息

- [Tauri 官方文档](https://tauri.app/)
- [Tauri API 文档](https://tauri.app/api/)
- [Rust 官方文档](https://www.rust-lang.org/)
