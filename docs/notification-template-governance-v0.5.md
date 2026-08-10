# TPIP 通知内容模板治理 v0.5

## 1. 目标

v0.5 将通知内容从 Worker 代码中分离为受治理资产，同时保持“设计态可变、执行态冻结”的原则：

- 模板拥有稳定身份与不可变版本；
- 变量通过受限占位符 DSL 和 Schema 声明，发布前编译；
- 渠道版本显式绑定已发布模板版本；
- Outbox 路由物化时完成渲染，Delivery 冻结最终报文；
- Worker 只执行冻结报文，不读取模板、数据库或设计态配置；
- 渲染失败进入可查询、可修复、可重路由的治理状态。

## 2. 资产模型

`NotificationTemplate` 是环境内唯一的稳定身份，支持 `ACTIVE/INACTIVE` 与 `rowVersion` 乐观锁。
`NotificationTemplateVersion` 是不可变内容版本，生命周期为 `DRAFT -> PUBLISHED`。发布后不允许原地修改，
新需求必须创建新版本并由新的渠道版本重新绑定。

模板版本包含：

- `providerType`：当前可执行类型为 `WEBHOOK`；
- `contentType`：当前限定 `application/json`；
- `templateDocument`：JSON 模板；
- `variableSchema`：变量名称、类型和必填约束；
- `referencedVariables`：编译获得的变量闭包；
- `contentChecksum`：规范化内容摘要。

渠道版本的 `templateVersionId` 可为空，以兼容 v0.4 的通用 Webhook 信封。绑定模板后，创建渠道版本会拒绝：

- 草稿模板版本；
- 已停用模板；
- 跨环境模板；
- Provider 类型不一致的模板。

## 3. 模板 DSL 与发布门禁

占位符格式为 `{{variable.path}}`。允许的根变量只有：

```text
eventId
eventType
aggregateType
aggregateId
environmentCode
payload.<safe.path>
```

不支持方法调用、条件表达式、脚本、网络访问、数据库访问或 Secret 读取。变量 Schema 采用受限的
JSON Schema 形态，属性名对应完整变量路径，支持 `string/number/integer/boolean/object/array`。

发布门禁要求：

- 模板必须是 JSON Object；
- 每个占位符都必须合法、在 `properties` 中声明并列入 `required`；
- Schema 中每个变量必须属于白名单；
- 完整字符串占位符保留原始 JSON 类型；
- 对象或数组不能嵌入普通文本；
- 模板和 Schema 规范化后再计算摘要。

示例：

```json
{
  "templateDocument": {
    "title": "{{eventType}}",
    "severity": "{{payload.severity}}",
    "attempts": "{{payload.attempts}}"
  },
  "variableSchema": {
    "type": "object",
    "properties": {
      "eventType": { "type": "string" },
      "payload.severity": { "type": "string" },
      "payload.attempts": { "type": "integer" }
    },
    "required": ["eventType", "payload.severity", "payload.attempts"]
  }
}
```

## 4. 配置与执行流程

```text
创建模板稳定身份
  -> 创建模板草稿版本
  -> 编译与变量闭包校验
  -> 发布模板版本
  -> 创建并发布绑定该模板的渠道版本
  -> 创建并发布引用该渠道版本的路由版本
  -> 事件写入 Outbox
  -> 路由物化并渲染模板
  -> Delivery 冻结 templateVersionId/contentType/messagePayload
  -> Worker 投递冻结报文
```

冻结点位于控制面路由事务中。因此模板或渠道后续发布新版本，不会改变已经生成的 Delivery；重试和
死信重放仍发送原报文，保证审计可复现和幂等语义稳定。

## 5. 失败治理

Outbox 路由状态扩展为：

```text
UNROUTED -> ROUTED
         -> NO_MATCH
         -> RENDER_FAILED
NO_MATCH/RENDER_FAILED -> UNROUTED
```

缺少必填变量、运行时变量类型不符或事件 Payload 不是合法 JSON 时，不创建任何 Delivery，事件进入
`RENDER_FAILED`，`routingError` 只记录安全错误描述，不记录变量值。模板修复并发布、相关资产重新启用，
或人工调用重路由 API 后，可以重新物化。

## 6. API

```text
POST /control/v1/notification-templates
GET  /control/v1/notification-templates
GET  /control/v1/notification-templates/{id}
POST /control/v1/notification-templates/{id}/versions
GET  /control/v1/notification-templates/{id}/versions
POST /control/v1/notification-templates/{id}/versions/{versionId}:publish
POST /control/v1/notification-templates/{id}:status
```

原通知路由失败 API 同时返回 `NO_MATCH` 和 `RENDER_FAILED`，响应增加 `routingStatus/routingError`。

## 7. 数据库迁移

Flyway V12 新增模板稳定表与模板版本表；渠道版本增加模板引用；Delivery 增加模板版本、内容类型和最终
报文快照；Outbox 增加安全渲染错误，并允许 `RENDER_FAILED` 路由状态。

## 8. 后续阶段

v0.6 建议实现 Provider 能力资产与专用适配器：企业微信、钉钉的消息结构编译、签名、频控、错误码归一，
同时避免把厂商差异重新泄漏到通用模板 DSL 与 Worker 调度内核。
