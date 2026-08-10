# TPIP Repository Instructions

- Java代码使用Java 21。
- 包名前缀使用`com.ftk.tpip`。
- 领域模块不得依赖Spring MVC、数据库Entity或具体HTTP客户端。
- Mapping Engine不得访问数据库、网络和Secret。
- Policy表达式不得读取Secret原文，只能传递Secret Reference。
- Runtime只执行已编译Bundle，不直接读取设计态Mapping或Policy配置。
- 新增功能必须包含对应自动化测试。
- 数据库环境信息未提供前，不提交Datasource连接配置或真实凭证。
- 已发布资产和Bundle采用不可变版本，不进行原地修改。
