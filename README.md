# 互动增强

为 Halo 提供用户装饰、身份标识和前台展示组件。

## 简介

互动增强插件用于增强站点互动场景中的用户展示效果。第一版聚焦装饰与身份展示，支持装饰资产管理、用户装饰授予、个人中心佩戴配置，以及面向主题和其他插件的前台展示能力。

## 开发环境

- Java 21+
- Node.js 18+
- pnpm

## 开发

```bash
# 启用插件
./gradlew haloServer
# 开发前端
cd ui
pnpm install
pnpm dev
```

## 构建

```bash
./gradlew build
```

构建完成后，可以在 `build/libs` 目录找到插件 jar 文件。

## 许可证

[GPL-3.0](./LICENSE) © Tim0x0 
