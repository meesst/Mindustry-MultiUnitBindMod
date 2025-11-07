# LUnitBindGroup 功能流程树形图

## LUnitBindGroup 主类
- **类结构**
  - 静态字段存储
    - `individualGroups`: 存储按控制器独立的单位组
    - `sharedGroups`: 存储按组名共享的单位组
    - `buildingToGroupName`: 记录处理器与共享组的关联
    - `paramCaches`: 存储处理器的参数缓存
    - `sharedGroupMaxCounts`: 存储共享组的最大count值
    - `sharedGroupConfigs`: 存储共享组的初始配置
  - **核心类**
    - `UnitGroupInfo`: 单位组信息类
    - `ParamCache`: 参数缓存类
    - `GroupConfig`: 共享组配置类
    - `UnitBindGroupStatement`: 逻辑语句类
    - `UnitBindGroupInstruction`: 指令执行类

## 工作流程

### 1. 初始化流程
- **创建UnitBindGroupStatement对象**
  - 设置默认参数：单位类型、数量、变量名等
  - 模式默认为1
- **构建指令**
  - 调用`build(LAssembler builder)`方法
  - 创建`UnitBindGroupInstruction`实例

### 2. 指令执行流程 (run 方法)
- **参数获取**
  - 获取单位类型、数量、变量名、组名等参数
- **模式判断**
  - 模式1：调用`executeMode1`
  - 模式2：调用`executeMode2`

### 3. 模式1执行流程 (executeMode1)
- **组名使用判断**
  - 有组名
    - 检查组名是否被其他处理器使用
      - 是：设置错误信息，返回
      - 否：使用共享组
  - 无组名：使用独立组
- **参数变化检查**
  - 检查单位类型、数量、模式、组名变化
  - 组名变化：清理旧组名关联
  - 参数变化：解绑所有单位，重新开始
- **更新参数缓存**
- **参数未变化且有单位组**
  - 更新单位索引，设置变量值
  - 返回
- **参数变化或无单位组**
  - 调用`updateUnitGroup`更新单位组

### 4. 模式2执行流程 (executeMode2)
- **共享组检查**
  - 检查共享组是否存在
    - 否：设置错误信息，返回
  - 获取当前单位
  - 当前单位有效：设置变量值
  - 当前单位无效：尝试查找第一个有效单位
    - 找到：设置变量值
    - 未找到：设置错误信息

### 5. 单位组更新流程 (updateUnitGroup)
- **共享组最大数量更新**
  - 更新共享组的最大count值
- **单位数量调整**
  - 单位数超过最大值：截断，重置索引
- **清理无效单位**
  - 创建有效单位列表
  - 检查单位有效性、所有权等
- **收集新单位**
  - 调用`collectAvailableUnits`获取可用单位
- **添加新单位到组**
  - 锁定单位，更新单位索引
- **设置变量值**

### 6. 辅助功能流程

#### 6.1 单位收集流程 (collectAvailableUnits)
- **单位类型解析**
- **收集所有单位**
  - 过滤单位类型
  - 过滤单位有效性
  - 调用`isUnitAvailableForController`检查可用性

#### 6.2 单位可用性检查 (isUnitAvailableForController)
- **基本检查**
  - 单位有效性
  - 团队一致性
  - 非玩家单位
- **控制器检查**
  - 独立模式：检查单位是否被其他控制器控制
  - 共享模式：检查单位是否被同组控制器控制

#### 6.3 单位锁定流程 (lockUnit)
- 设置单位控制器为当前建筑
- 更新单位AI为LogicAI
- 取消原有的命令AI

#### 6.4 内存清理流程 (cleanupMemoryAndUnusedGroups)
- 定期执行（每分钟）
- 清理无效控制器
- 清理未使用的组

#### 6.5 未使用组清理 (cleanupUnusedGroup)
- 检查组的最后访问时间
- 超过清理时间：移除组，解锁单位

## 错误处理流程
- **组名冲突错误**
- **组不存在错误**
- **无可用单位错误**
- **单位无效错误**

## 模式功能对比

### 模式1：单位控制模式
- **功能**：创建、管理、控制单位组
- **操作**：抓取单位、锁定单位、管理单位组
- **适用场景**：需要控制单位的主处理器

### 模式2：共享组访问模式
- **功能**：只读访问已存在的共享单位组
- **操作**：获取单位、读取索引
- **适用场景**：需要访问其他处理器控制的单位的辅助处理器

## 数据结构关系
- **Building ↔ String** (buildingToGroupName): 处理器与组名的映射
- **Building ↔ UnitGroupInfo** (individualGroups): 处理器与独立组的映射
- **String ↔ UnitGroupInfo** (sharedGroups): 组名与共享组的映射
- **Building ↔ ParamCache** (paramCaches): 处理器与参数缓存的映射