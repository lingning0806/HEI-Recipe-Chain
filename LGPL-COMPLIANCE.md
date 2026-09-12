# LGPL 跨文件组合复核 · 2026-09-13

## 结论与范围

当前实现采用“分别保留库许可，完整提供组合所需源码和构建材料”的方式准备分发。依据LGPL-2.1第6(a)/(d)节、LGPL-3第4(d)(0)节；额外提供可分离库及源码，对应LGPL-2.1第7节和LGPL-3第5节的单独取得要求。不以同一JAR内含多种许可为理由统一改成MIT，也不自动改成GPL。

这是针对当前源码与交付材料的履约复核，不是对任意后续改编的法律保证。

## 实际代码边界

1. LGPL-2.1-only：mezz.jei.autocrafting.toposort下的TopologicalSort、StronglyConnectedComponentDetector、CyclePresentException及其包说明。其来源/版权头保留。该单元依赖Java与Guava，不引用mezz.jei.nova返还物计算单元。本地与HEI基线的对比未对这些源码作新的修改。
2. LGPL-3.0-or-later：RemainderFlow、ExecutionSchedule、ChainMath、InputUsage、RecipeInputUsageProvider；只使用Java标准类型或本单元类型，不调用toposort或Minecraft。以模型输入和返回值与主体通信。采用较宽的许可范围以覆盖已声明的RecipeChainMath模型改编。
3. HEI主体如RecipeChain调用上述两个单元的公开接口。当前未把二者算法正文互相嵌入、跨库继承或合并为一个派生库。基于这个具体边界，可以分别履行两份LGPL，不需要把LGPL-2.1-only升级为LGPL-3。
4. 若将来把一个库的实现复制进另一个库，必须重新检查派生作品及兼容性，不能沿用本结论。反射调用AE不是判断本项目LGPL边界的依据。

## 本次分发材料

- 完整HEI源码、API、资源、测试、Gradle wrapper及构建脚本。
- JAR内META-INF/HEI-LICENSES及声明；模组信息credits中注明主要第三方版权归属及许可位置。
- lgpl-parts/中的拓扑排序库、返还物计算库及各自源码。它们从同一次编译生成，未混入其他模组；不要额外放入mods。
- BUILD.md与RELINK.md说明重建与替换，允许用户安装修改后的版本；不增加禁止修改或调试性逆向的条款。
- 对象代码和对应源码应作为同一GitHub Release附件同时公开。只发JAR或只链接不断变化的默认分支不足以落实这个发布方案。

## 源码可得性

本地包尚未上传，网络分发义务在真正发布时落实：必须把固定版本源码压缩包与二进制一起放入同一Release，源码免费可下载。不得把本地文件路径当作公开源码链接。
库源码包同时含许可、来源和构建/重新组合说明。完整依赖声明在完整源码中。Minecraft、AE2、NEE不是此JAR中随附的库，本包不复制这些游戏依赖。

## 依据

- https://www.gnu.de/documents/lgpl-2.1.en.html （LGPL2.1正文镜像，第2、4、6、7节）
- https://www.gnu.org/licenses/lgpl-3.0.html.en （LGPL3，第0、2、4、5节）
- https://www.gnu.org/licenses/gpl-faq.en.html#AllCompatibility （需区分复制代码与使用库）
- 本项目LICENSES/LGPL-2.1.txt、LGPL-3.0.txt、GPL-3.0.txt保留全文。
