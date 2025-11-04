# Mindustry-MultiUnitBindMod

一个为Mindustry游戏添加多单位绑定功能的逻辑扩展模组。

## 功能介绍

### ubindgroup 指令
该指令允许玩家同时绑定多个同类型单位，并可以控制最大绑定数量。

**语法:**
```
ubindgroup unitType maxCount varUnit varIndex
```

**参数说明:**
- `unitType`: 单位类型，用于筛选要绑定的单位
- `maxCount`: 最大绑定数量，限制同时绑定的单位数量
- `varUnit`: 存储单位对象的变量名
- `varIndex`: 存储当前单位索引的变量名

**使用场景:**
- 批量控制多个同类型单位
- 实现更复杂的单位编队控制
- 与ubind和ucontrol等指令配合使用，实现更灵活的单位管理

## 安装方法
1. 编译mod生成jar文件
2. 将jar文件放入Mindustry游戏的mods文件夹
3. 启动游戏，在模组列表中启用该mod

## 开发信息
作者: meesst