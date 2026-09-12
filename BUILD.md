# 构建与源码对应

## 获取对应版本

公开 suite10 标签为 `v4.29.13-nova.10-suite10`。它基于 HEI 4.29.13 的提交 `7236ccd55888b709269c2ad1c264f043ceb96205` 并包含本分支修改；仅检出上游基线不能构建本版本。

```sh
git clone https://github.com/lingning0806/HEI-Recipe-Chain.git
cd HEI-Recipe-Chain
git checkout v4.29.13-nova.10-suite10
```

Release 中的完整源码 ZIP 对应该标签的 522 个受版本控制文件。main 后续包含文档更新；需要还原发布版时请使用标签或同版源码附件。

## 构建环境

- Gradle wrapper 8.13，RetroFuturaGradle 1.4.9。
- 构建宿主 Java 17，需要可用的 Java 8 工具链，编译目标 Java 8。
- 游戏实测 Java 21；这是运行环境，不是构建宿主要求。
- 首次构建需要联网获取公开依赖；尚未在全空缓存环境完成验证。

```sh
chmod +x gradlew
./gradlew build --no-daemon -PnovaCompileOnly -x extractNatives2 -x extractNatives3
```

本机 Apple Silicon、已有依赖缓存时通过的命令：

```sh
./gradlew build --offline --no-daemon -PnovaCompileOnly -x extractNatives2 -x extractNatives3 -x downloadVanillaJars -x downloadFernflower
```

这些跳过项不代表所有平台的通用首次构建命令。构建产物位于 `build/libs/`。不需要运行 publish/curseforge/modrinth 发布任务。

## 已完成的检查

独立源码目录首次构建完成 38 项任务；后续构建与相关回归通过。初次离线缺少的 jst-cli-bundle 经联网补齐，`tags.properties` 已包含在源码中。420 个上游基线类未发现不兼容删除；返还计算单元通过独立 Java 8 目标编译。

## 发布 JAR 与实测开发包的区别

公开包经独立构建，修正 `mcmod.info` 版本、移除多余的 `Tags 2.class`，加入分项许可和来源资源；相关源码增加许可注释，未改变合成算法。许可注释可能影响 class 调试行号，因此不宣称最终 JAR 与开发包逐字节一致，也未承诺可重复构建得到相同字节。

公开包摘要和验证记录见 Release 的 `SHA256SUMS`、`VALIDATION.json`。GitHub 下载的最终 JAR 尚待单独游戏内复验；开发包的维护者实测范围见[兼容说明](docs/COMPATIBILITY.md)。

## 修改 LGPL 组件

组件边界、源码与重新组合步骤见 [RELINK.md](RELINK.md) 和 [LGPL-COMPLIANCE.md](LGPL-COMPLIANCE.md)。组件 JAR 是开发附件，不要作为额外模组安装。
