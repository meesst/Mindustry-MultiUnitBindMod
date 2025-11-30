# Mindustry-LogicExtendMod

一个为Mindustry游戏添加多种逻辑扩展功能的模组，支持字符串处理、弹药自定义、函数定义和嵌套逻辑等高级功能。

## 功能介绍

### 1. 字符串合并指令 (stringmerge)
该指令允许玩家将两个字符串合并为一个字符串，扩展了Mindustry逻辑系统的字符串处理能力。

**语法:**
```
stringmerge outputVar string1 string2
```

**参数说明:**
- `outputVar`: 存储合并结果的变量名
- `string1`: 第一个要合并的字符串
- `string2`: 第二个要合并的字符串

**示例:**
```
stringmerge result "Hello, " "World!"
```

### 2. 弹药创建与设置指令
该指令集允许玩家创建和自定义各种类型的弹药，包括基本子弹、炸弹、激光、闪电、导弹、火焰和火炮等。

#### createammo 指令
创建一种新的弹药类型。

**语法:**
```
createammo ammoType id
```

**参数说明:**
- `ammoType`: 弹药类型，可选值包括BasicBullet、BombBullet、LaserBullet、LightningBullet、MissileBullet、FireBullet、ArtilleryBullet
- `id`: 用于标识该弹药的唯一ID

#### setammo 指令
修改、删除或在世界中创建弹药。

**语法:**
```
setammo operation ammoProperty id value [team] [x] [y] [rotation]
```

**参数说明:**
- `operation`: 操作类型，可选值包括set（修改属性）、remove（删除弹药）、create（在世界中创建弹药）
- `ammoProperty`: 要修改的弹药属性，如damage、speed、lifetime等
- `id`: 弹药的唯一ID
- `value`: 要设置的属性值
- `team`（可选）: 团队ID，用于create操作
- `x`（可选）: X坐标，用于create操作
- `y`（可选）: Y坐标，用于create操作
- `rotation`（可选）: 旋转角度，用于create操作

### 3. 嵌套逻辑指令 (nestedlogic)
该指令允许玩家在逻辑代码中嵌套其他逻辑代码，支持多层嵌套，与主逻辑共享变量作用域。

**语法:**
```
nestedlogic "encodedNestedCode"
```

**参数说明:**
- `encodedNestedCode`: Base64编码的嵌套逻辑代码

**功能特性:**
- 支持多层嵌套
- 与主逻辑共享变量作用域
- 提供可视化编辑器
- 支持语言包
- 添加了tooltip提示

### 4. 函数指令 (function)
该指令允许玩家定义和调用自定义函数，提高逻辑代码的复用性和可读性。

## 安装方法
1. 编译mod生成jar文件
2. 将jar文件放入Mindustry游戏的mods文件夹
3. 启动游戏，在模组列表中启用该mod

## 使用示例

### 字符串合并示例
```
stringmerge greeting "Hello, " "Commander!"
print greeting
```

### 弹药创建与使用示例
```
# 创建一种新的基本子弹
createammo BasicBullet "mybullet"
# 设置子弹属性
setammo set damage "mybullet" 50
setammo set speed "mybullet" 10
# 在世界中创建子弹
setammo create "mybullet" "player" @sharded 500 500 0
```

### 嵌套逻辑示例
```
# 主逻辑
set x 10
set y 20
# 嵌套逻辑，与主逻辑共享变量
nestedlogic "c2V0IHogPSAqIHggeSAKcHJpbnQgeng="
```

## 开发信息
作者: meesst
版本: 1.0.0
兼容性: Mindustry 151.1+

## 注意事项
- 确保合理设置最大绑定数量，避免过多占用单位资源
- 在共享组模式下，多个控制器共享单位池，请注意协调控制逻辑
- 使用模式2时，确保已经有控制器在模式1下创建并填充了共享组
- 当不再需要控制单位时，建议显式解绑以释放资源
- 嵌套逻辑指令支持多层嵌套，但请注意不要过度嵌套，以免影响性能
- 弹药创建和设置指令需要特权权限，仅在服务器端或单机游戏中可用

## 更新日志

### v1.0.0
- 初始版本
- 添加了字符串合并指令
- 添加了弹药创建和设置指令
- 添加了函数指令
- 添加了嵌套逻辑指令
- 支持自定义类别和图标
- 支持语言包
- 添加了tooltip提示

## 贡献
欢迎提交Issue和Pull Request，帮助改进这个模组！

## 许可证
本项目采用MIT许可证，详见LICENSE文件。
