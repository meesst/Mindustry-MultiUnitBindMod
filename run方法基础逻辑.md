
# 参数描述：
type：要绑定的单位类型
count：要绑定的单位数量
mode：绑定模式，可选值:Capture-unit（捕获单位）或visiting-unit（访问单位）
group：单位绑定组的名称，用于识别不同的单位组，group="stand-alone"时代表(独立单位组:当前逻辑块独立使用的单位组),group!="stand-alone"时代表(共享单位组:可以在多个逻辑块中共享使用的单位组)
unitVar：用于存储当前单位的变量名,或错误提示
indexVar：用于存储单位索引的变量名（≥1）,错误返回-1
# run方法完整逻辑设计

## 核心功能概述
run方法是单位绑定系统的主执行方法，根据不同的模式执行访问或抓取单位的操作，同时处理各种错误情况，并通过unitVar和indexVar返回结果或错误信息。

## 完整执行流程（流水线设计）

```
开始
  │
  ├─► 1. 根据mode分流
  │     │
  │     ├─► visiting-unit模式
  │     │     ├─► 查找指定group的单位池（由UI管理，组必定存在）
  │     │     │     ├─► 检查组的使用状态
  │     │     │     │     ├─► 未使用：unitVar="组未被使用"，indexVar=-1，返回
  │     │     │     │     └─► 已使用：
  │     │     │     │           ├─► 单位池为空：unitVar="单位池为空"，indexVar=-1，返回
  │     │     │     │           └─► 单位池非空：使用该单位池
  │     │     └─► 单位池操作（不涉及绑定/解绑/维护）
  │     │           └─► 跳转到索引处理逻辑
  │     │
  │     └─► Capture-unit模式
  │           ├─► 处理单位池创建
  │           │     ├─► group="stand-alone"：获取独立单位池
  │           │     └─► group≠"stand-alone"：
  │           │     ├─► 检查共享单位池是否已被使用
  │           │     │     ├─► 已被使用：unitVar="组已被使用"，indexVar=-1，返回
  │           │     │     └─► 未被使用：使用该共享单位池
  │           │
  │           ├─► 调用绑定方法绑定单位到池中
  │           │     ├─► 绑定成功（至少有一个单位）：
  │           │     │     ├─► 更新指定组的使用状态为已使用
  │           │     │     └─► 继续
  │           │     └─► 绑定失败（无符合条件单位）：
  │           │           ├─► 更新指定组的使用状态为未使用
  │           │           └─► unitVar="无符合条件单位"，indexVar=-1，返回
  │           │
  │           └─► 单位池维护
  │                 ├─► 维护单位池，确保池中单位满足type和绑定规则条件
  │                 └─► 检查维护后单位池是否为空
  │                       ├─► 单位池为空：
  │                       │     ├─► 更新指定组的使用状态为未使用
  │                       │     └─► unitVar="单位池为空"，indexVar=-1，返回
  │                       └─► 单位池非空：继续
  │
  ├─► 2. 索引处理逻辑（适用于所有单位池，包括独立池和共享池）
  │     ├─► 获取单位池当前索引
  │     ├─► 索引取模确保在有效范围内（防止越界）
  │     ├─► 获取当前索引对应的单位
  │     ├─► 设置unitVar为当前单位
  │     ├─► 设置indexVar为当前索引+1（索引从1开始）
  │     └─► 索引递增（循环访问）
  │
  └─► 3. 返回结果
        └─► 返回unitVar和indexVar的值
结束
```

## 详细实现说明

### 1. visiting-unit模式处理
- **直接查找**：直接查找指定group的单位池
- **只读操作**：不涉及单位绑定、解绑或维护
- **错误处理**：
  - 组未使用：unitVar="组未被使用"，indexVar=-1
  - 单位池为空：unitVar="单位池为空"，indexVar=-1

### 2. Capture-unit模式处理
- **单位池管理**：
  - 独立单位池：每个逻辑块独有，直接获取
  - 共享单位池：多逻辑块共享，由UI玩家手动创建和管理，只需检查是否已被使用
- **单位绑定**：根据绑定规则绑定满足条件的单位
- **单位池维护**：
  - 维护单位池，确保池中单位满足type和绑定规则条件
  - 检查维护后单位池是否为空
  - 若为空，更新组使用状态为未使用，并返回错误
- **状态管理**：根据绑定和维护结果动态更新单位池的使用状态

### 3. 索引处理逻辑（适用于所有单位池）
```
// 确保计数器在有效范围内循环（防止索引越界）
currentIndex %= unitPool.size();
// 获取当前索引对应的单位
unitVar = unitPool.get(currentIndex);
// 设置索引值（从1开始）
indexVar = currentIndex + 1;
// 索引递增，下次执行时将返回下一个单位
currentIndex++;
```

### 4. 错误处理机制
- **无符合条件**：unitVar="无符合条件单位"，indexVar=-1
- **组已使用**：unitVar="组已被使用"，indexVar=-1
- **组未使用**：unitVar="组未被使用"，indexVar=-1
- **单位池为空**：unitVar="单位池为空"，indexVar=-1（适用于visiting-unit模式和Capture-unit模式维护后）

### 5. 单位池使用状态管理
- **状态定义**：
  - 已使用状态：表示该单位池已被某个逻辑块绑定了单位并正在使用
  - 未使用状态：表示该单位池没有被绑定单位或已被重置
- **状态转换条件**：
  - 从未使用→已使用：在Capture-unit模式下，成功绑定至少一个单位后
  - 从已使用→未使用：
    1. 在Capture-unit模式下，绑定失败（无符合条件单位）
    2. 在Capture-unit模式下，单位池维护后为空
    3. 通过UI手动调用重置方法
    4. 通过UI手动调用删除方法
- **模式差异**：
  - visiting-unit模式：仅检查和使用已有状态，不改变单位池的使用状态
  - Capture-unit模式：根据绑定和维护结果动态更新单位池的使用状态
- **共享单位池特殊处理**：共享单位池的使用状态由系统统一管理，任何逻辑块都可以访问已使用的共享池，但只有在池未使用时才能进行绑定操作

### 6. 重要注意事项
- 重置方法、添加方法、删除方法仅用于UI手动维护，不在run方法中调用
- 访问模式不参与单位绑定、解绑和维护操作
- 抓取模式参与完整的绑定、解绑和维护流程
- 所有错误情况通过unitVar返回错误信息，indexVar返回-1
- 正常情况下indexVar返回的索引值从1开始递增

# 绑定方法：绑定指定类型（type），指定数量（count）到指定组索引（group）的单位池里。
1. 使用绑定规则绑定指定类型和指定数量单位到单位池
2. 预控制单位（将单位的控制方设置为当前逻辑处理器）
- 实现原理：通过创建LogicAI实例并设置为单位控制器，参考`d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1\core\src\mindustry\logic\LExecutor.java`文件中第288-297行的`checkLogicAI`方法
- 设置流程：创建新的LogicAI对象，将其controller属性设置为当前逻辑处理器的building，然后调用unit.controller(LogicAI实例)方法
- 具体实现：`unit.controller(la)`（第297行）将LogicAI设置为单位的控制器，同时清除单位的旧状态
## 绑定规则：绑定满足以下条件的单位
1. 单位必须是有效的（即未死亡且已添加到游戏世界中）
   - 参考源代码：`d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1\core\src\mindustry\entities\comp\HealthComp.java` 第16行的 `isValid()` 方法
2. 单位未受到任何有效控制（即sensor指令中controlled分支返回值为0）
   - 参考源代码：`d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1\core\src\mindustry\entities\comp\UnitComp.java` 第281行的 `sense(LAccess sensor)` 方法中的 `controlled` 分支
   - 不受逻辑处理器控制（不是LogicAI类型）
   - 不受玩家控制（不是Player类型）
   - 不受命令系统控制或没有活动命令（不是CommandAI类型或CommandAI没有活动命令）

# 解绑方法：解绑指定组索引的单位池里的指定单位，参考游戏源代码中的实现：
1. 解绑实现原理：参考`d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1\core\src\mindustry\logic\LExecutor.java`第336行的unbind指令实现 - 调用unit.resetController()方法
2. resetController方法详情：参考`d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1\core\src\mindustry\entities\comp\UnitComp.java`第443-445行 - 调用controller(type.createController(self()))，将单位控制器重置为单位类型默认的控制器，使单位恢复为标准AI控制

# 重置方法：重置指定组索引的单位池，调用解绑方法解绑指定组索引的单位池里面所有单位。

# 添加方法：通过指定组索引，创建一个单位池。

# 删除方法：删除指定组索引的单位池，并调用解绑方法解绑单位池里面的所有单位。

# 单位池维护方法：单位池维护基于以下关键条件，确保单位池中的单位满足type和绑定规则条件，并在必要时更新使用状态
1. 单位存活判断：
- 参考源代码：`d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1\core\src\mindustry\entities\comp\HealthComp.java` 第16行的 `isValid()` 方法
   - 当单位已死亡时（isValid()=false），需要调用解绑方法从单位池中移除
2. 控制方判断：参考`d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1\core\src\mindustry\entities\comp\UnitComp.java`第301-302行的senseObject方法中controller分支的实现 - 如果单位无效则返回null - 如果控制器是LogicAI类型则返回log.controller（逻辑处理器） - 否则返回单位自身
   - 当单位被其他处理器控制或不再受当前处理器控制时，需要调用解绑方法从单位池中移除
3. 单位类型判断：
   - 当单位类型不再匹配type参数时，需要调用解绑方法从单位池中移除

