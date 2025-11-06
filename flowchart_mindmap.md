# 单位控制器执行流程（更新版）

## 整体执行流程

```mermaid
graph TD
    A[开始执行] --> B{控制器有效性检查}
    B -->|无效| C[清理资源]
    C --> Z[结束]
    B -->|有效| D[获取并处理组名称]
    D --> E{模式判断}
    E -->|模式1| Mode1[执行模式1逻辑]
    E -->|模式2| Mode2[执行模式2逻辑]
    Mode1 --> Z
    Mode2 --> Z
```

## 模式1：抓取模式（核心管理流程）

```mermaid
graph TD
    M1_Start[模式1开始] --> M1_GroupCheck{组名指定判断}
    M1_GroupCheck -->|是| M1_GroupNameUsed{检查组名使用情况}
    M1_GroupNameUsed -->|已被使用| M1_ErrorGroupUsed[设置错误]
    M1_ErrorGroupUsed --> M1_End[模式1结束]
    M1_GroupNameUsed -->|未被使用| M1_UseShared[使用共享组]
    M1_GroupCheck -->|否| M1_UseIndividual[使用独立组]
    M1_UseShared --> M1_GetParams[获取单位参数]
    M1_UseIndividual --> M1_GetParams
    M1_GetParams --> M1_ParamsChanged{参数变化检查}
    M1_ParamsChanged -->|有变化| M1_ResetStart[重新开始]
    M1_ResetStart --> M1_TypeChanged{单位类型有变化}
    M1_TypeChanged --> M1_CountChanged{单位数量有变化}
    M1_CountChanged --> M1_ModeChanged{模式有变化}
    M1_ModeChanged --> M1_GroupNameChanged{组名有变化}
    M1_GroupNameChanged --> M1_CleanupOld[清理旧组名关联]
    M1_CleanupOld --> M1_ParamsChanged
    M1_ParamsChanged -->|无变化| M1_CountCheck{单位数量检查}
    M1_CountCheck -->|数量<0| M1_ErrorCount[设置错误]
    M1_ErrorCount --> M1_End
    M1_CountCheck -->|数量>=0| M1_UpdateUnits[更新组单位]
    M1_UpdateUnits --> M1_GroupType{组类型}
    M1_GroupType -->|共享组| M1_UpdateShared[更新共享组单位并记录映射]
    M1_GroupType -->|独立组| M1_UpdateIndividual[更新独立组单位并移除映射]
    M1_UpdateShared --> M1_UpdateBind[更新单位绑定并返回]
    M1_UpdateIndividual --> M1_UpdateBind
    M1_UpdateBind --> M1_End
```

## 参数变化检查

```mermaid
graph TD
    PC_Start[参数变化检查开始] --> PC_GetCache[获取参数缓存]
    PC_GetCache --> PC_CheckChanged{检查变化}
    PC_CheckChanged -->|单位类型变化| PC_NeedRestart[需要重新执行]
    PC_CheckChanged -->|单位数量变化| PC_NeedRestart
    PC_CheckChanged -->|模式变化| PC_NeedRestart
    PC_CheckChanged -->|组名变化| PC_Cleanup[清理旧关联]
    PC_Cleanup --> PC_NeedRestart
    PC_NeedRestart --> PC_UpdateCache[更新参数缓存]
    PC_CheckChanged -->|无变化| PC_NoRestart[不需要重新执行]
    PC_UpdateCache --> PC_End[参数变化检查结束]
    PC_NoRestart --> PC_End
```

## 模式2：访问模式（简化衍生流程）

```mermaid
graph TD
    M2_Start[模式2开始] --> M2_GroupExists{共享组检查}
    M2_GroupExists -->|不存在| M2_ErrorNotExist[设置错误]
    M2_ErrorNotExist --> M2_End[模式2结束]
    M2_GroupExists -->|存在| M2_SetVars[设置单位变量和索引]
    M2_SetVars --> M2_End
```

## 设计说明

1. **模式1参数检查逻辑修改**
   - 移除了单位类型检查，只保留数量≥0的检查
   - 参数变化检查移至模式1内部，在获取单位参数后执行
   - 参数变化时清理单位池和缓存，重新开始流程

2. **参数变化检查位置调整**
   - 从checkAndUpdateParams方法移至executeMode1方法内
   - 简化了checkAndUpdateParams方法，只保留必要的逻辑

3. **参数变化检测逻辑**
   - 单位类型变化：当单位类型参数改变时触发重新执行
   - 单位数量变化：当数量参数改变时触发重新执行
   - 模式变化：当模式参数改变时触发重新执行
   - 组名变化：当组名参数改变时，清理旧的组名关联后触发重新执行

4. **流程特点**
   - 共享组支持：允许多个控制器共享同一单位组
   - 独立组支持：为不指定组名的控制器创建独立单位组
   - 错误处理：对各种异常情况设置明确的错误信息
   - 资源管理：包含清理机制，避免资源泄漏

## 代码结构概览

- `run()`: 主执行入口，处理控制器有效性和模式分支
- `executeMode1()`: 实现模式1逻辑，包括组名判断、参数变化检查、单位数量检查和组单位更新
- `executeMode2()`: 实现模式2逻辑，包括共享组检查和单位变量设置
- 辅助方法: 提供单位收集、更新、锁定等功能支持