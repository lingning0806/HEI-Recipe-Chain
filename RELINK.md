# 修改、重建与替换 LGPL 部分

推荐从同一Release下载完整源码压缩包，解压后按BUILD.md执行构建。修改src/main/java/mezz/jei/autocrafting/toposort或src/main/java/mezz/jei/nova中已标注的LGPL单元，保持对外接口兼容，再运行构建及测试。

构建命令示例：
```sh
./gradlew build -PnovaCompileOnly -x extractNatives2 -x extractNatives3
```

如已有准备好的依赖缓存，可使用BUILD.md中的离线验证命令。产物在build/libs。退出Minecraft，备份旧JAR和配置，用重建JAR替换旧HEI，保持modid jei，不能将两份HEI一起加载。本项目未引入签名锁、许可服务器或只允许原二进制运行的检查。

不要求用户去掉原许可或放弃修改权。除正常API兼容和游戏运行依赖外，没有本项目增加的安装限制。用户也可利用Java/JAR工具重新组合编译后的兼容类；完整重建更容易保留混淆映射及资源。

lgpl-parts是为单独获取库代码提供的开发文件，不是独立模组；单独拓扑排序库还需Guava，返还物计算单元只需Java8标准库。单独源码包不代替完整HEI构建包。
