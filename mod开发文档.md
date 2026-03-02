# Mindustry-MultiUnitBindMod 开发文档

## 项目概述
Mindustry-MultiUnitBindMod 是一个为 Mindustry 游戏开发的 mod，主要实现多单位绑定相关功能，扩展游戏的逻辑指令系统。该 mod 允许玩家创建和管理单位绑定组，实现更复杂的单位控制和逻辑指令执行。

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
│       └── LUnitBindGroupUI.java  # 单位绑定组UI实现
└── mod开发文档.md        # 开发文档（当前文件）
```

## 主要类和接口

### 1. 核心逻辑扩展类
- **LUnitBindGroupUI**: 实现单位绑定组功能的主要UI类，包含逻辑指令的创建和执行
  - **UnitBindGroupStatement**: 继承自LStatement，实现单位绑定组的逻辑语句定义
  - **UnitBindGroupI**: 继承自LExecutor.LInstruction，实现单位绑定组指令的具体执行逻辑

### 2. 使用的游戏内置类
- **mindustry.logic.LExecutor**: 游戏逻辑执行器
- **mindustry.logic.LStatement**: 逻辑语句基类，用于创建自定义逻辑指令
- **mindustry.entities.units.Unit**: 游戏单位基类
- **mindustry.gen.EntityGroup**: 实体组，用于管理多个单位
- **arc.scene.ui.layout.Table**: UI布局类，用于构建指令界面
- **arc.struct.Seq**: 序列容器类，用于存储频道列表等数据

## 技术实现细节

### 1. UI组件实现详情

#### Table布局系统
- **类路径**：`arc.scene.ui.layout.Table`
- **核心功能**：Mindustry UI系统的核心布局组件，提供网格状UI元素排列
- **主要接口**：
  - `defaults()`：设置表格内所有元素的默认布局属性（边距、大小、对齐方式等）
  - `add()`：向当前行添加UI元素
  - `row()`：开始新的一行
  - `table()`：创建嵌套表格
  - `button()`：添加按钮组件
  - `margin()/pad()`：设置表格边距和内边距
  - `left()/center()/right()`：设置水平对齐方式
  - `top()/middle()/bottom()`：设置垂直对齐方式
- **实现方式**：使用嵌套Table结构创建复杂UI，每个Table对象可以包含子Table或其他UI元素
- **LUnitBindGroupUI中的应用**：
  - 使用menuTable作为最外层容器
  - mainContent作为主Table容器，内部嵌套channelList和addSection两个子容器
  - 每个频道项使用独立的Table对象(row)实现

#### Button组件
- **创建方式**：通过Table类的`button()`方法创建
- **主要接口**：
  - `button(String text, Runnable listener)`：创建带文本和点击事件的按钮
  - `button(String text, Icon icon, Runnable listener)`：创建带文本、图标和点击事件的按钮
  - `clicked(Runnable listener)`：设置按钮点击事件
  - `size(float width, float height)`：设置按钮大小
  - `pad(float pad)`：设置按钮内边距
- **LUnitBindGroupUI第200行实现**：
  ```java
  addSection.button("Add", () -> {
      // 添加新频道的逻辑实现
      String input = newChannelInput.getText();
      if(input != null && !input.isEmpty() && !UnitBindGroupStatement.channels.contains(input)){
          UnitBindGroupStatement.channels.add(input);
          // 保存和刷新UI的逻辑
      }
  });
  ```
  - 使用lambda表达式定义按钮点击事件的回调函数
  - 实现了输入验证和频道添加逻辑

### 2. 逻辑指令扩展机制
- 基于游戏现有的逻辑执行器进行扩展
- 通过继承LStatement类创建自定义逻辑指令UnitBindGroupStatement
- 实现指令的UI展示、参数配置和执行逻辑

### 2. 单位绑定组功能
- 支持创建和管理多个单位绑定频道
- 默认包含"stand-alone"频道，不允许删除
- 每个频道可以绑定多个单位，实现批量控制
- 使用Core.settings存储频道配置，实现持久化保存

### 3. UI实现细节
- 使用Table和自定义布局实现直观的频道选择界面
- 实现频道列表的动态更新和管理（添加/删除频道）
- 为每个频道项提供删除按钮（除默认频道外）
- 支持自定义频道名称输入，带输入验证
- 所有UI元素采用左对齐布局，统一视觉效果

### 4. 指令执行机制
- UnitBindGroupI类负责指令的实际执行逻辑
- 支持多种操作模式，包括单位捕获和控制
- 通过变量系统将执行结果反馈给逻辑系统

### 5. 指令标签多语言悬浮提示实现
#### 核心实现机制
- **实现原理**：利用Mindustry游戏的LCanvas.tooltip()静态方法为指令、分支和参数添加多语言悬浮提示
- **关键类和方法**：
  - `LStatement.showSelect()`：为指令分支自动添加悬浮提示的方法
  - `LStatement.param()`：为参数标签添加多语言悬浮提示的方法
  - `LCanvas.tooltip()`：实际处理悬浮提示显示的底层方法
  - `Core.bundle`：访问游戏多语言系统的核心实例

#### 悬浮提示类型及键名构建规则
Mindustry支持三种类型的悬浮提示，每种类型有不同的键名规则：

##### 1. 整个指令的悬浮提示
- **作用**：描述指令的整体功能和用途
- **键名格式**：`lst.指令名`（指令名全小写，例如：`lst.radar`）
- **查找机制**：Mindustry自动查找，无需额外代码
- **效果**：在指令选择列表和指令标题上显示

##### 2. 指令分支的悬浮提示
- **作用**：描述指令的各个分支功能
- **键名格式**：`枚举类名.分支名`（全小写，例如：`fastunitcontroltype.itemtake`）
- **查找机制**：`showSelect()`方法自动查找
- **效果**：在分支选择按钮上显示
- **自动回退机制**：如果找不到`枚举类名.分支名`，会回退查找`lenum.分支名`

##### 3. 分支参数的悬浮提示
- **作用**：描述分支中各个参数的用途
- **键名格式**：`指令名.分支名.参数名`（全小写，例如：`fastunitcontrol.itemtake.from`）
- **查找机制**：需要手动调用`tooltip()`方法并指定键名
- **效果**：在参数字段上显示

#### 自动转换规则
- **重要注意事项**：Mindustry引擎的LCanvas.tooltip()方法会自动将键名转换为全小写并移除空格
- **命名规范**：在定义tooltip键名时必须使用全小写格式，不能使用驼峰命名法
- **错误示例**：`unitBindGroup.type`（驼峰命名，不会生效）
- **正确示例**：`unitbindgroup.type`（全小写，会正常生效）

#### 具体实现步骤

##### 1. 整个指令的悬浮提示
- **实现方式**：无需代码修改，直接在语言包中添加
- **语言包示例**：
  ```properties
  lst.fastunitcontrol = 快速单位控制指令，无CD限制，支持多种分支操作
  ```

##### 2. 指令分支的悬浮提示
- **实现方式**：使用`showSelect()`方法自动添加
- **代码示例**：
  ```java
  // 分支选择按钮
  table.button(b -> {
      b.label(() -> type.name());
      b.clicked(() -> showSelect(b, FastUnitControlType.values(), type, t -> {
          type = t;
          rebuild(table);
      }, 2, cell -> cell.size(120, 50)));
  }, Styles.logict, () -> {}).size(120, 40);
  ```
- **语言包示例**：
  ```properties
  fastunitcontroltype.itemtake = 从建筑中快速取出物品（无CD）
  fastunitcontroltype.itemdrop = 快速将物品放入建筑（无CD）
  ```

##### 3. 分支参数的悬浮提示
- **实现方式**：手动调用`tooltip()`方法
- **代码示例**：
  ```java
  // 添加参数字段和悬浮提示
  fields(table, paramName, paramValue, paramSetter)
      .width(100f)
      .self(elem -> tooltip(elem, "fastunitcontrol." + type.name().toLowerCase() + "." + paramName.toLowerCase()));
  ```
- **语言包示例**：
  ```properties
  fastunitcontrol.itemtake.from = 要从中取出物品的建筑
  fastunitcontrol.itemtake.item = 要取出的物品类型
  fastunitcontrol.itemtake.amount = 要取出的物品数量
  ```

#### 支持平台
- **自动适配**：悬浮提示系统会自动适配不同平台的交互方式
- **桌面版**：鼠标悬停显示提示
- **移动版**：长按显示提示

#### 完整代码示例
```java
// 快速单位控制指令完整悬浮提示实现
public class FastUnitControlStatement extends LStatement {
    public FastUnitControlType type = FastUnitControlType.itemTake;
    public String p1 = "0", p2 = "0", p3 = "0";
    
    @Override
    public void build(Table table) {
        rebuild(table);
    }
    
    private void rebuild(Table table) {
        table.clearChildren();
        table.left();
        
        // 分支选择按钮（自动添加分支悬浮提示）
        table.button(b -> {
            b.label(() -> type.name());
            b.clicked(() -> showSelect(b, FastUnitControlType.values(), type, t -> {
                type = t;
                rebuild(table);
            }, 2, cell -> cell.size(120, 50)));
        }, Styles.logict, () -> {}).size(120, 40);
        
        row(table);
        
        // 分支参数（手动添加参数悬浮提示）
        for(int i = 0; i < type.params.length; i++) {
            final int index = i;
            String paramName = type.params[i];
            
            fields(table, paramName, index == 0 ? p1 : index == 1 ? p2 : p3, 
                   index == 0 ? v -> p1 = v : index == 1 ? v -> p2 = v : v -> p3 = v)
                .width(100f)
                .self(elem -> tooltip(elem, "fastunitcontrol." + type.name().toLowerCase() + "." + paramName.toLowerCase()));
        }
    }
}
```

#### 语言包完整示例
```properties
# 1. 整个指令的悬浮提示
lst.fastunitcontrol = 快速单位控制指令，无CD限制，支持多种分支操作

# 2. 指令分支的悬浮提示
fastunitcontroltype.itemtake = 从建筑中快速取出物品（无CD）
fastunitcontroltype.itemdrop = 快速将物品放入建筑（无CD）
fastunitcontroltype.paytake = 快速拾取载荷（无CD）
fastunitcontroltype.paydrop = 快速卸下载荷（无CD）
fastunitcontroltype.assist = 快速协助其他单位建造（无CD）

# 3. 分支参数的悬浮提示
fastunitcontrol.itemtake.from = 要从中取出物品的建筑
fastunitcontrol.itemtake.item = 要取出的物品类型
fastunitcontrol.itemtake.amount = 要取出的物品数量
fastunitcontrol.itemdrop.to = 要放入物品的建筑
fastunitcontrol.itemdrop.amount = 要放入的物品数量
```

## 版本兼容性
- 当前开发版本基于 Mindustry v151.1
- UI实现遵循游戏现有界面风格，确保视觉一致性
- 已适配游戏的Table和Cell布局系统，使用正确的边距和对齐方法

## 开发流程
1. 在参考游戏源码 `d:\Users\zhang\MYcode\Mindustry\Mindustry-151.1` 中查找相关实现
2. 遵循游戏源代码的编程风格和命名约定
3. 完成代码后直接提交到仓库，不进行本地构建测试
4. 功能测试通过后，使用口令"更新mod开发文档"维护文档

## 更新记录
- 初始版本：创建基本项目结构和文档框架
- v0.1.0：实现LUnitBindGroupUI核心功能，包括单位绑定组创建、管理和指令执行
- v0.1.1：修复UI布局问题，优化频道列表显示，正确实现Table的边距设置
- v0.1.2：修复tooltip不生效问题，将所有键名前缀从驼峰命名法`unitBindGroup`改为全小写`unitbindgroup`，并更新相关文档说明

*注：本文档将在功能测试通过后持续更新，作为项目知识库使用。*