# TPIP Fixture Assertion Profile 1.0

## 1. 定位

Fixture Assertion Profile（FAP）是 FixtureSuite 的标准断言协议。它把“测试代码里的 if/equals”转换为
可配置、可编译、可版本化、可审计的验证资产。断言随 FixtureSuiteVersion 一起计算 SHA-256，版本发布后
不可修改。

FAP 是受限声明式 DSL，不执行 Groovy、JavaScript、SpEL 或任意代码，不允许访问数据库、网络和 Secret。

## 2. 配置与执行流程

```text
编辑 FixtureCase assertions
  -> 控制面语法和适用阶段校验
  -> JSONPath / JSON Schema / Policy 表达式预编译校验
  -> 规范化 JSON 并计算 FixtureSuiteVersion checksum
  -> 发布不可变 FixtureSuiteVersion
  -> Verification Engine 生成实际执行上下文
  -> 逐条执行断言（不短路）
  -> VerificationCheck 保存每条断言的状态和 actual 证据
  -> 全部断言通过，该 Fixture Check 才通过
```

创建阶段采用 fail-fast，执行阶段采用 collect-all。配置错误不能进入版本；运行时断言失败则完整收集，便于
一次看到所有契约差异。

## 3. 断言文档

`FixtureCase.assertions` 是最多 100 项的 JSON 数组。每项必须有版本内唯一的 `code` 和固定 `type`。

```json
[
  {"code":"mapping-success","type":"SUCCESS","expected":true},
  {"code":"customer-id","type":"JSON_PATH","path":"$.customerId","operator":"EQUALS","expected":"C1001"},
  {"code":"response-contract","type":"JSON_SCHEMA","schema":{"type":"object","required":["customerId"]}},
  {"code":"active-customer","type":"POLICY_EXPRESSION","expression":"$.status == \"ACTIVE\""}
]
```

旧字段 `expected`、`expectedSuccess`、`expectedDiagnosticCode` 保持兼容；新建资产应优先使用 `assertions`。

## 4. 标准断言类型

| 类型 | 作用 | Mapping Fixture | REMOTE_CALL Fixture |
|---|---|---:|---:|
| `SUCCESS` | 比较执行成功状态 | 支持 | 支持 |
| `DIAGNOSTIC_CODE` | 判断诊断码集合是否包含期望值 | 支持 | 支持 |
| `JSON_PATH` | 对 Body 的确定性路径取值后比较 | 支持 | 支持 |
| `JSON_SCHEMA` | 使用 TPIP 受控 JSON Schema Profile 校验 Body | 支持 | 支持 |
| `POLICY_EXPRESSION` | 执行受限布尔业务规则 | 支持 | 支持 |
| `HTTP_STATUS` | 比较 HTTP 状态码 | 禁止 | 支持 |
| `HTTP_HEADER` | 按大小写不敏感的 Header 名比较 | 禁止 | 支持 |

HTTP 类型只能用于显式 `executionMode=REMOTE_CALL` 的 Fixture；Mapping Fixture 创建阶段始终拒绝。远程调用
功能默认关闭，并受幂等等级、环境、主机、端口、HTTPS、超时、大小和调用数量门禁约束。

## 5. JSONPath 与操作符

JSONPath 使用 TPIP 确定性子集：根 `$`、对象属性和确定数组索引，例如 `$.data.items[0].id`。禁止递归、
过滤器、通配符、函数和脚本表达式。

操作符：

- `EXISTS`、`NOT_EXISTS`
- `EQUALS`、`NOT_EQUALS`：按 JSON 类型严格比较，字符串 `"1"` 不等于数字 `1`
- `CONTAINS`：字符串包含、数组成员包含，或对象包含指定属性名
- `MATCHES`：仅用于字符串，版本创建时按有界正则 Profile 预编译；禁止分组、分支、反向引用等高风险结构

## 6. JSON Schema Profile

复用 Runtime Contract Validator 的受控结构校验能力，支持 `type`、`required`、`properties`、`items`、
`enum`、`const`、`additionalProperties`、字符串/数字/数组边界和 `pattern`。不支持的断言关键字 fail closed，
避免配置者误以为某项约束已经生效。

## 7. Policy 表达式

Policy 表达式用于简单跨字段业务结论，语法限定为：

```text
<selector> (== | !=) <JSON scalar>
```

Mapping Fixture 可使用 `$...` 和 `success`；REMOTE_CALL 上下文还可使用 `http.status`、`header.<name>`。
示例：`$.status == "ACTIVE"`、`success == true`、`http.status != 500`。

它与集成 Policy DSL 遵守同一安全原则，但职责不同：集成 Policy 改变调用管道行为，FAP Policy 只读取
验证上下文并返回布尔结论，不能修改请求、响应或运行状态。

## 8. 存储与兼容

迁移 `V24__fixture_case_assertion_document.sql` 为 `tpip_fixture_case` 增加 JSON 类型的
`assertion_document`。断言文档是 FixtureSuiteVersion 内容 checksum 的一部分。历史数据无需迁移，仍按
旧期望字段执行；新旧模型不能改变已发布版本的内容。

## 9. REMOTE_CALL

REMOTE_CALL v0.1 已按默认关闭方式实现，详细执行流程、配置和安全边界见
`docs/workspace-remote-call-fixture-v0.1.md`。当前仅接受 `IDEMPOTENT`，不接受带键幂等或非幂等操作。
