# HEI Recipe Chain Enhancements

Minecraft 1.12.2 的 HEI 非官方增强分支。维护者：**绫宁（Xingmingfu）**。
基于 [CleanroomMC/HadEnoughItems](https://github.com/CleanroomMC/HadEnoughItems) 4.29.13，设计参考 [StardustMINUS-01/JustEnoughItems](https://github.com/StardustMINUS-01/JustEnoughItems)。

## 功能

- 配方链规划、材料偏好和依赖显示排序。
- 催化剂、可重复使用工具、容器返还与缺口计算。
- AE取料、批量合成、缺料后的剩余目标续做。
- 整链合成样板预检、去重、批量编码及逐项结果。
- 材料明细、折叠视图、拖拽搜索及配方投影。

## 安装与兼容

modid仍为`jei`，**不要和原HEI或JEI同时加载**。退出游戏、备份原HEI和配置/存档，将本JAR替换进mods后重启。回退时移出本JAR，恢复原版；必要时恢复配置备份。

已验证：Minecraft 1.12.2 / Cleanroom，新星工程隔离实例，Java21，AE2 UEL v0.56.8-novaeng_ver配合NEE。未测试Forge环境、其他AE分支、多人服务器或所有扩展终端可用。样板编码仅覆盖已适配终端的合成模式。

指向配方组：Shift+C合成/续做，Shift+T从AE取料，Shift+M材料明细，Shift+P预检并再次按键编码。点击括号切换视图，拖动括号合并，Ctrl+Z撤销书签编辑。快捷键以游戏设置为准。

维护者已实测：16份AE合成、向AE补料续做、工作台批量、桶/工具返还，以及重进后的书签/偏好/分组保存。自动回归另覆盖数量守恒、排序、样板策略和兼容接口。

## 构建与修改

见 [BUILD.md](BUILD.md) 和 [RELINK.md](RELINK.md)。首次准备开发依赖需要联网。源码快照包含构建输入和测试，不包含Minecraft或其他模组的二进制。

## 许可

本项目采用分项许可。MIT基础/新增贡献、LGPL-2.1-only拓扑排序、LGPL-3.0-or-later返还计算单元、Apache-2.0搜索代码、CC-BY-2.5取色代码分别适用。
详见 [LICENSE-OVERVIEW.md](LICENSE-OVERVIEW.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [LGPL-COMPLIANCE.md](LGPL-COMPLIANCE.md)。
发布时提供同版完整源码与可分离LGPL库源码；本项目不限制用户修改库或为调试修改而逆向工程。库附件是开发材料，不要额外放入mods。

源码仓库：https://github.com/Xingmingfu/HEI-Recipe-Chain
下载与对应源码：https://github.com/Xingmingfu/HEI-Recipe-Chain/releases
问题反馈：https://github.com/Xingmingfu/HEI-Recipe-Chain/issues
