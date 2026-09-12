# 构建与源码对应

基线 commit：7236ccd55888b709269c2ad1c264f043ceb96205；快照包含suite10未提交修改，不能仅checkout基线重建。
源码中的 src/ 与已实测JAR所用工作区逐文件一致。快照仅替换README并关闭gradle.properties中的三个发布开关；保留上游README副本。

Gradle wrapper 8.13，RetroFuturaGradle 1.4.9，编译目标Java8。Gradle宿主使用Java17；需要可用Java8工具链，游戏实测使用Java21。
首次构建需要联网获取公开依赖，不能保证在空缓存环境下离线构建。

```sh
chmod +x gradlew
./gradlew build --no-daemon -PnovaCompileOnly -x extractNatives2 -x extractNatives3
```

本机已有开发缓存时验证命令：
```sh
./gradlew build --offline --no-daemon -PnovaCompileOnly -x extractNatives2 -x extractNatives3 -x downloadVanillaJars -x downloadFernflower
```
这些跳过项针对本机Apple Silicon旧原生依赖下载，不能视为所有平台都适用的首次构建命令。
不运行publish/curseforge/modrinth任务。本包不附任何发布凭据。未声明构建产物逐字节可复现。

## 本次快照验证结果

独立目录构建已通过，31项任务成功。初次离线缺少jst-cli-bundle，经联网补齐后继续；导出清单补入injectTags所需tags.properties，重新构建通过。快照未使用原工程build目录。仍依赖本机公开依赖缓存，未声称全空缓存构建成功。

发布binary采用独立重建产物：正常.class与实测包逐项一致；删除实测包中的多余Tags 2.class，mcmod.info版本从suite1修正为suite10。SHA256见VALIDATION.json。此包尚未另外加载游戏，原实例未更换。

## GitHub发布候选复核

独立github-ready目录首次构建38项任务通过，回归通过。返还计算单元额外通过Java17 javac --release 8独立编译，无Minecraft/HEI依赖。许可证及来源声明现已内置JAR。正常算法源码只增加许可注释，未改算法；模组credits增加来源署名。420基线类无不兼容删除。游戏实例未更换。
