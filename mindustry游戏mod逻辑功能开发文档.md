# Mindustry多单位绑定逻辑指令MOD开发文档

## 1. 项目概述

### 1.1 项目背景
在Mindustry游戏中，逻辑处理器提供了强大的自动化控制能力，但目前的单位绑定机制(ucontrol)存在限制，每次只能控制一个单位。虽然可以通过现有逻辑指令组合实现多单位管理，但过程复杂且效率低下。本项目旨在开发一个简化的MOD，提供更便捷的多单位绑定指令，与游戏现有指令深度结合，使玩家能够轻松实现多单位控制。

### 1.2 项目目标
- 开发核心逻辑指令`ubindgroup`，简化多单位绑定过程
- 确保与游戏现有逻辑指令无缝协作
- 优化性能，避免不必要的复杂实现
- 确保MOD在多人游戏中的兼容性
- 提供详细的使用文档和示例

### 1.3 技术栈
- Java (Mindustry使用的主要开发语言)
- Mindustry API (游戏核心API)
- Arc Framework (Mindustry使用的游戏框架)

## 2. 功能模块设计

### 2.1 核心指令：ubindgroup

**功能描述**：在逻辑循环执行过程中，自动依次绑定单位组中的不同单位，简化多单位控制流程。

**实现方式**：
- 创建新的逻辑指令`ubindgroup`，按单位类型筛选并管理多个单位
- 维护内部单位列表，但不对外暴露复杂的组管理接口
- 通过返回参数机制与现有逻辑指令无缝集成

**核心参数**：
- 参数1：要绑定的单位种类（UnitType或单位ID）
- 参数2：要绑定的最大单位数量
- 参数3：返回参数，用于存储当前获取的单位对象引用
- 参数4：返回参数，用于存储当前单位在组中的编号（从1开始）

## 3. 技术架构设计

### 3.1 MOD结构

```
Mindustry-MultiUnitBindMod/
├── src/
│   └── logicExtend/                 # 逻辑扩展包
│       ├── LEMain.java              # MOD主类
│       ├── LUnitBindGroup.java      # 多单位绑定指令实现
│       ├── LStringMerge.java        # 字符串合并指令
│       ├── LAmmo.java               # 弹药相关指令
│       ├── LFunction.java           # 函数相关指令
│       ├── LCategoryExt.java        # 自定义分类
│       └── LEIcon.java              # 图标资源
├── mod.json                         # MOD配置文件
├── README.md                        # MOD说明文档
└── mindustry游戏mod逻辑功能开发文档.md  # 开发文档
```

### 3.2 核心类设计

#### LEMain.java
- MOD主类，负责初始化和注册自定义逻辑指令
- 重写`loadContent()`方法，调用各指令的`create()`静态方法进行注册

#### LUnitBindGroup.java
- 包含两个核心内部类：
  - `UnitBindGroupStatement`：负责UI构建和指令参数解析
  - `UnitBindGroupInstruction`：负责指令的实际执行逻辑
- 通过`create()`静态方法注册自定义解析器和语句到LogicIO

### 3.3 数据流程

**ubindgroup指令工作流程**：
1. 逻辑处理器执行`ubindgroup`指令
2. 指令根据单位类型参数筛选符合条件的单位
3. 维护内部单位列表，确保单位有效性
4. 在逻辑循环中，每次执行时依次返回单位组中的不同单位
5. 将当前单位引用和单位编号写入指定的返回变量
6. 当遍历完所有单位后，重新从第一个单位开始

## 4. API接口规范

### 4.1 MOD主类

```java
public class MultiUnitBindMod extends Mod {
    @Override
    public void init() {
        // 初始化MOD
    }
    
    @Override
    public void loadContent() {
        // 注册逻辑指令
    }
}
```

### 4.2 ubindgroup指令实现

```java
@RegisterStatement("ubindgroup")
public class UnitBindGroupStatement extends LStatement {
    // 参数设置
    public String unitType = "dagger";
    public String count = "10";
    public String unitVar = "currentUnit";
    public String indexVar = "unitIndex"; // 必需参数
    
    @Override
    public void build(Table table) {
        // UI构建逻辑，添加参数输入框
        // 单位类型、数量、返回变量、索引变量
        // 使用Table和相关组件构建用户界面
        table.add("Type: ");
        table.addField(unitType, text -> unitType = text).size(100f);
        table.row();
        
        table.add("Max: ");
        table.addField(count, text -> count = text).size(100f);
        table.row();
        
        table.add("Unit var: ");
        table.addField(unitVar, text -> unitVar = text).size(100f);
        table.row();
        
        table.add("Index var: ");
        table.addField(indexVar, text -> indexVar = text).size(100f);
    }
    
    @Override
    public LInstruction build(LAssembler builder) {
        return new UnitBindGroupInstruction(
            builder.var(unitType),
            builder.var(count),
            builder.var(unitVar),
            builder.var(indexVar) // 必需参数
        );
    }
    
    // 静态方法，用于注册指令
    public static void create() {
        // 注册自定义解析器
        LAssembler.customParsers.put("ubindgroup", (reader, line) -> {
            // 解析指令参数
            // 返回新的UnitBindGroupStatement实例
        });
        
        // 将指令添加到LogicIO.allStatements
        LogicIO.allStatements.add(UnitBindGroupStatement.class);
    }
    
    @Override
    public Category category() {
        return LCategoryExt.function; // 使用自定义分类
    }
}

public class UnitBindGroupInstruction implements LInstruction {
    private LVar unitType;
    private LVar count;
    private LVar unitVar;
    private LVar indexVar; // 必需
    
    // 存储每个逻辑控制器的单位组和当前索引
    private static final ObjectMap<LogicControllable, UnitGroupInfo> groups = new ObjectMap<>();
    
    public UnitBindGroupInstruction(LVar unitType, LVar count, LVar unitVar, LVar indexVar) {
        this.unitType = unitType;
        this.count = count;
        this.unitVar = unitVar;
        this.indexVar = indexVar;
    }
    
    @Override
    public void run(LExecutor exec) {
        // 获取当前逻辑控制器
        LogicControllable controller = exec.controller;
        
        // 获取或创建单位组信息
        UnitGroupInfo info = groups.get(controller);
        if(info == null) {
            info = new UnitGroupInfo();
            groups.put(controller, info);
        }
        
        // 获取单位类型和数量参数
        Object typeObj = unitType.get(exec);
        int maxCount = (int)count.num(exec);
        
        // 确保单位组是最新的
        updateUnitGroup(info, typeObj, maxCount, exec.team);
        
        // 循环遍历单位组
        if(!info.units.isEmpty()) {
            // 更新当前索引
            info.currentIndex = (info.currentIndex + 1) % info.units.size;
            
            // 获取当前单位
            Unit unit = info.units.get(info.currentIndex);
            
            // 写入返回变量
            exec.setVariable(unitVar.name, unit);
            
            // 写入单位索引（从1开始计数）
            exec.setVariable(indexVar.name, info.currentIndex + 1);
        } else {
            // 没有找到单位，清空返回变量
            exec.setVariable(unitVar.name, null);
            exec.setVariable(indexVar.name, 0);
        }
    }
    
    // 更新单位组
    private void updateUnitGroup(UnitGroupInfo info, Object typeObj, int maxCount, Team team) {
        // 清除无效单位并重新填充
        info.units.clear();
        
        if(typeObj instanceof UnitType type) {
            // 获取指定类型的单位
            for(Unit unit : Vars.world.units()) {
                if(unit.type == type && unit.team == team && unit.isValid()) {
                    info.units.add(unit);
                    if(info.units.size >= maxCount) break;
                }
            }
        } else if(typeObj instanceof String typeName) {
            // 通过名称查找单位类型
            UnitType type = UnitTypes.get(typeName);
            if(type != null) {
                for(Unit unit : Vars.world.units()) {
                    if(unit.type == type && unit.team == team && unit.isValid()) {
                        info.units.add(unit);
                        if(info.units.size >= maxCount) break;
                    }
                }
            }
        }
    }
    
    // 内部类，用于存储单位组信息
    private static class UnitGroupInfo {
        public final Seq<Unit> units = new Seq<>();
        public int currentIndex = -1; // 初始为-1，第一次执行时会变为0
    }
}
```

## 5. 数据模型定义

### 5.1 内部数据结构

```java
// 使用ObjectMap存储每个逻辑控制器的单位组信息
private static final ObjectMap<LogicControllable, UnitGroupInfo> groups = new ObjectMap<>();

// 单位组信息类
private static class UnitGroupInfo {
    public Seq<Unit> units = new Seq<>();      // 单位列表
    public int currentIndex = -1;              // 当前单位索引
}
```

### 5.2 MOD配置文件(mod.json)

```json
{
  "displayName": "MultiUnitBind - 多单位绑定",
  "name": "multi-unit-bind",
  "author": "meesst",
  "main": "logicExtend.LEMain",
  "description": "A mod that adds multi-unit binding functionality\n一个添加了多单位绑定功能的模组，允许同时控制多个同类型单位",
  "version": "1.0.0",
  "minGameVersion": 151.1,
  "java": true
}
```

## 6. 开发环境配置

### 6.1 开发环境设置

1.使用github云端编译

### 6.2 编译与测试

1. **编译MOD**
   - 使用Gradle编译项目：`./gradlew build`
   - 或使用IDE的编译功能

2. **测试MOD**
   - 编译后的MOD文件位于`build/libs/`目录
   - 将MOD文件复制到Mindustry的mods目录
   - 启动Mindustry测试MOD功能

## 7. 部署流程

### 7.1 MOD打包

```bash
# 使用Gradle打包MOD
1.使用git命令上传到云端仓库（https://github.com/meesst/Mindustry-MultiUnitBindMod）
```

### 7.2 安装与分发

1. **本地安装**
   - 将打包好的MOD文件(.zip格式)复制到Mindustry的mods目录
   - Windows: `%AppData%/Mindustry/mods/`
   - Linux: `~/.local/share/Mindustry/mods/`
   - macOS: `~/Library/Application Support/Mindustry/mods/`

2. **多人游戏注意事项**
   - 所有客户端和服务器必须安装相同版本的MOD
   - 确保MOD版本与游戏版本兼容

## 8. 关键实现细节

### 8.1 指令参数处理

ubindgroup指令的核心在于参数处理和返回值机制：

```java
// 参数解析示例
Object typeObj = unitType.obj();
int maxCount = (int)count.num();

// 返回值处理
if(!info.units.isEmpty()) {
    // 获取当前单位
    Unit unit = info.units.get(info.currentIndex);
    
    // 写入返回变量
    exec.setVariable(unitVar.name, unit);
    
    // 写入索引变量
    if(indexVar != null) {
        exec.setVariable(indexVar.name, info.currentIndex + 1);
    }
}
```

### 8.2 单位组维护

为确保单位组的有效性，每次执行指令时都需要更新单位列表：

```java
private void updateUnitGroup(UnitGroupInfo info, Object typeObj, int maxCount, Team team) {
    // 清除无效单位并重新填充
    info.units.clear();
    
    if(typeObj instanceof UnitType type) {
        Seq<Unit> units = team.data().unitCache(type);
        if(units != null) {
            for(Unit unit : units) {
                if(unit.isValid() && unit.team == team) {
                    info.units.add(unit);
                    if(info.units.size >= maxCount) break;
                }
            }
        }
    }
}
```

### 8.3 循环遍历机制

通过维护当前索引，实现逻辑循环中对所有单位的依次处理：

```java
// 更新当前索引，实现循环遍历
info.currentIndex = (info.currentIndex + 1) % info.units.size;
```

## 9. 使用示例

### 9.1 基本使用示例

```
# 设置参数
set unitCount 5
set unitType @dagger
set currentUnit null
set unitIndex 0

# 使用ubindgroup指令绑定单位
ubindgroup unitType unitCount currentUnit unitIndex

# 检查是否成功绑定单位
jump 14 notEqual currentUnit null
print "没有找到可用单位"
stop

# 使用游戏自带的ubind确保单位引用正确
ubind currentUnit

# 使用ucontrol控制单位移动到目标位置
ucontrol move 1000 1000 0 0 0

# 可以根据单位索引设置不同的行为
jump 22 equal unitIndex 1
ucontrol target @thisx @thisy+500 1 0 0
jump 24 always
ucontrol guard @thisx @thisy 0 0 0
```

### 9.2 高级使用示例

```
# 初始化参数
set maxUnits 8
set unitType @poly
set unit null
set index 0
set cycle 0

mainLoop:
# 递增循环计数器
op add cycle cycle 1

# 绑定多单位
ubindgroup unitType maxUnits unit index

# 检查是否绑定成功
jump loopEnd notEqual unit null
print "没有找到单位"
stop

# 单位操作
ubind unit

# 根据索引分配不同任务
oj mod task index 3 0

# 任务0: 攻击最近敌人
jump task0 equal task 0
oj mod task index 3 0

# 任务1: 收集资源
jump task1 equal task 1

# 任务2: 防御基地
task2:
ucontrol guard @thisx @thisy 0 0 0
jump taskEnd always

task0:
ucontrol target @thisx @thisy 1 0 0
jump taskEnd always

task1:
ucontrol mine @thisx @thisy 0 0 0

taskEnd:
# 循环控制
jump mainLoop always

loopEnd:
print "循环结束"
```

## 10. 性能优化建议

1. **限制单位数量**：根据逻辑处理器类型设置合理的最大单位数量
   - 小逻辑：建议不超过10个单位
   - 中逻辑：可支持20-30个单位
   - 大逻辑：可支持更多单位，但仍需注意性能影响

2. **优化单位更新频率**：可以通过时间判断减少单位列表更新频率

3. **避免重复绑定**：如果不需要重新选择单位，可以在循环中仅执行一次ubindgroup

## 11. 注意事项与限制

1. **多人游戏要求**：服务器和所有客户端必须安装相同版本的MOD
2. **单位所有权**：只能绑定属于自己队伍的单位
3. **性能影响**：绑定过多单位可能导致逻辑处理器性能下降
4. **版本兼容性**：确保MOD版本与游戏版本兼容

## 12. 后续扩展计划

1. 添加单位过滤条件，支持更精确的单位选择
2. 增加单位健康度、能量等状态过滤
3. 优化大规模单位的处理性能
4. 提供单位组状态查询功能

---

本文档提供了Mindustry多单位绑定逻辑指令MOD的完整开发指南，基于简化的设计理念，专注于提供便捷的多单位绑定功能，与游戏现有指令无缝集成。开发者可以根据此文档实现功能完整的MOD，并根据实际需求进行扩展和优化。