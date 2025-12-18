# Mindustry-LogicExtendMod

一个为Mindustry游戏添加多种逻辑扩展功能的模组，支持嵌套逻辑、单位绑定控制、字符串处理、弹药自定义等高级功能。

## 功能介绍

### 1. 嵌套逻辑指令 (nestedlogic)
该指令允许玩家在逻辑代码中实现调用栈和嵌套执行，支持三种操作类型：

#### 核心功能
- **push操作**：将变量或值压入指定栈的指定索引
- **call操作**：执行嵌套的逻辑代码块
- **pop操作**：从指定栈的指定索引读取值到变量
- **多栈支持**：可以创建和使用多个独立的栈
- **自动回收**：超时的栈元素会自动被清理
- **嵌套深度限制**：防止无限递归

#### 语法
```
nestedlogic push <variable> <index> <stackName>
nestedlogic call <logicName> <encodedNestedCode>
nestedlogic pop <variable> <index> <stackName>
```

#### 功能特性
- 支持多层嵌套（最大深度5层）
- 与主逻辑共享变量作用域和链接
- 提供可视化编辑器
- 支持语言包和tooltip提示

### 2. 单位绑定组指令 (unitBindGroup)
该指令允许玩家绑定和控制指定类型和数量的单位。

#### 核心功能
- **单位类型绑定**：可以绑定到特定单位类型（如@poly、@dagger等）
- **数量控制**：指定要绑定的单位数量
- **三种工作模式**：
  - 模式1：直接控制单位
  - 模式2：共享控制模式
  - 模式3：高级控制模式
- **单位变量存储**：将当前单位和索引存储到指定变量

#### 语法
```
unitBindGroup <type> <count> <mode> <unitVar> <indexVar>
```

#### 参数说明
- `type`：目标单位类型标识（如@poly、@dagger）
- `count`：要绑定的单位数量
- `mode`：控制模式（1、2、3）
- `unitVar`：存储当前单位的变量名
- `indexVar`：存储单位索引的变量名

### 3. 字符串合并指令 (stringmerge)
该指令允许玩家将两个字符串合并为一个字符串。

#### 语法
```
stringmerge <outputVar> <string1> <string2>
```

#### 参数说明
- `outputVar`：存储合并结果的变量名
- `string1`：第一个要合并的字符串
- `string2`：第二个要合并的字符串

#### 功能特性
- 支持合并数字和字符串
- 结果长度限制为220字符
- 与Mindustry逻辑系统无缝集成

### 4. 弹药创建与设置指令
该指令集允许玩家创建和自定义各种类型的弹药。

#### createammo 指令
创建一种新的弹药类型。

**语法:**
```
createammo <ammoType> <id>
```

**参数说明:**
- `ammoType`：弹药类型，可选值包括BasicBullet、BombBullet、LaserBullet、LightningBullet、MissileBullet、FireBullet、ArtilleryBullet
- `id`：用于标识该弹药的唯一ID

#### setammo 指令
修改、删除或在世界中创建弹药。

**语法:**
```
setammo <operation> <ammoProperty> <id> <value> [team] [x] [y] [rotation]
```

**参数说明:**
- `operation`：操作类型，可选值包括set（修改属性）、remove（删除弹药）、create（在世界中创建弹药）
- `ammoProperty`：要修改的弹药属性，如damage、speed、lifetime等
- `id`：弹药的唯一ID
- `value`：要设置的属性值
- `team`（可选）: 团队ID，用于create操作
- `x`（可选）: X坐标，用于create操作
- `y`（可选）: Y坐标，用于create操作
- `rotation`（可选）: 旋转角度，用于create操作

### 5. 快速单位控制指令 (fastunitcontrol)
该指令提供了多种快速控制单位的操作，允许绕过原版单位控制的冷却时间限制，实现更高效的单位控制。

#### 核心功能
- **物品操作**：快速从建筑取物和向建筑放物
- **Payload操作**：快速获取和放置Payload（单位或建筑）
- **协助建造**：协助其他单位进行建造
- **无冷却限制**：绕过原版单位控制的冷却时间

#### 操作类型
1. **itemTake**：从指定建筑获取物品
   - 参数：`from`（来源建筑）、`item`（物品类型）、`amount`（数量）
   - 功能：从指定建筑中获取指定数量的物品

2. **itemDrop**：将物品放入指定建筑或丢弃
   - 参数：`to`（目标建筑）、`amount`（数量）
   - 功能：将当前物品放入指定建筑或丢弃

3. **payTake**：获取Payload（单位或建筑）
   - 参数：`takeUnits`（是否获取单位）、`x`（X坐标）、`y`（Y坐标）
   - 功能：在指定位置获取Payload（可以是单位或建筑）

4. **payDrop**：放置Payload
   - 参数：`x`（X坐标）、`y`（Y坐标）
   - 功能：在指定位置放置当前Payload

5. **assist**：协助其他单位建造
   - 参数：`assister`（协助者单位）、`target`（目标单位）
   - 功能：让协助者单位复制目标单位的建造计划并协助建造

#### 语法
```
fastunitcontrol <type> <param1> <param2> <param3>
```

#### 优势
- 无冷却时间限制，可实现更频繁的单位操作
- 支持多种单位控制操作，功能全面
- 与原版单位控制指令兼容
- 提高单位控制效率，适合自动化逻辑

#### 应用场景
- 高效的资源运输系统
- 快速的Payload处理
- 协作建造系统
- 高频单位操作逻辑

### 6. 自定义类别扩展
扩展了逻辑指令的类别，为新指令提供合适的分类。

### 7. 调试日志系统
提供可配置的调试日志功能，帮助开发者和玩家排查问题。

## 安装方法
1. 编译mod生成jar文件
2. 将jar文件放入Mindustry游戏的mods文件夹
3. 启动游戏，在模组列表中启用该mod

## 使用示例

### 嵌套逻辑示例 - push和pop操作
```
# 定义变量
set myVar 100
# 将变量压入默认栈的索引0
nestedlogic push myVar 0 default
# 清空变量
set myVar 0
# 从栈中恢复变量值
nestedlogic pop myVar 0 default
# 输出结果（应该是100）
print myVar
```

### 单位绑定组示例
```
# 绑定3个多足单位到变量currentUnit和indexUnit，使用模式2
unitBindGroup @poly 3 2 currentUnit indexUnit
# 控制当前单位移动
ucontrol currentUnit move 100 100
```

### 字符串合并示例
```
# 合并两个字符串
stringmerge greeting "Hello, " "Commander!"
# 输出结果
print greeting
```

### 弹药创建示例
```
# 创建一种新的基本子弹
createammo BasicBullet "mybullet"
# 设置子弹属性
setammo set damage "mybullet" 50
setammo set speed "mybullet" 10
```

## 开发信息
作者: meesst
版本: 1.0.1
兼容性: Mindustry 151.1+

## 注意事项
- 嵌套逻辑指令支持多层嵌套，但请注意不要超过最大深度5层
- 确保合理设置最大绑定数量，避免过多占用单位资源
- 在共享组模式下，多个控制器共享单位池，请注意协调控制逻辑
- 当不再需要控制单位时，建议显式解绑以释放资源
- 弹药创建和设置指令需要特权权限，仅在服务器端或单机游戏中可用
- 可以在游戏设置中启用调试日志，帮助排查问题

## 更新日志

### v1.0.1
- 添加了128*128像素的mod图标
- 修改了build.gradle配置，确保图标被正确包含在编译后的jar包中

### v1.0.0
- 初始版本
- 添加了嵌套逻辑指令（push、call、pop）
- 添加了单位绑定组指令
- 添加了字符串合并指令
- 添加了弹药创建和设置指令
- 添加了快速单位控制功能
- 支持自定义类别和图标
- 支持语言包和tooltip提示
- 添加了可配置的调试日志功能

## 贡献
欢迎提交Issue和Pull Request，帮助改进这个模组！

## 许可证
本项目采用MIT许可证，详见LICENSE文件。