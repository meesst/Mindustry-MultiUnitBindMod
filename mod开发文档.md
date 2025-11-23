# Mindustry-MultiUnitBindMod 开发文档

## 项目概述
Mindustry-MultiUnitBindMod 是一个为 Mindustry 游戏开发的 mod，主要实现多单位绑定相关功能，扩展游戏的逻辑指令系统。

## 项目结构

```
Mindustry-MultiUnitBindMod/
├── .github/             # GitHub 工作流配置
├── .gitignore           # Git 忽略文件配置
├── assets/              # 游戏资源文件
│   └── bundles/         # 多语言捆绑包
├── build.gradle         # Gradle 构建配置
├── gradle/              # Gradle 包装器
├── mod.json             # Mod 配置信息
├── src/                 # 源代码目录
│   └── logicExtend/     # 逻辑指令扩展实现
└── mod开发文档.md        # 开发文档（当前文件）
```

## 主要类和接口

### 1. 核心逻辑扩展类
- **LogicExtend**: 扩展游戏逻辑指令系统的主要类
- **UnitBindController**: 处理单位绑定逻辑的控制器类

### 2. 使用的游戏内置类
- **mindustry.logic.LExecutor**: 游戏逻辑执行器
- **mindustry.entities.units.Unit**: 游戏单位基类
- **mindustry.gen.EntityGroup**: 实体组，用于管理多个单位

## 技术实现细节

### 1. 逻辑指令扩展机制
- 基于游戏现有的逻辑执行器进行扩展
- 添加新的指令类型以支持单位绑定操作
- 使用游戏提供的 API 与单位系统交互



## 版本兼容性
- 当前开发版本基于 Mindustry v151.1
- 需关注游戏版本更新时可能的 API 变化
- 已实现关键接口的适配层以提高兼容性

## 开发流程
1. 在参考游戏源码 `d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1` 中查找相关实现
2. 遵循游戏源代码的编程风格和命名约定
3. 完成代码后直接提交到仓库，不进行本地构建测试
4. 功能测试通过后，使用口令"更新mod开发文档"维护文档

## 更新记录
- 初始版本：创建基本项目结构和文档框架

*注：本文档将在功能测试通过后持续更新，作为项目知识库使用。*