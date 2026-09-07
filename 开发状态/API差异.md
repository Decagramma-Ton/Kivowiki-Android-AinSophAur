# 0.3.0 角色协议复核

2026-09-07 完成六类角色、24 维筛选及原生媒体所需的公开协议核对，详细接口与边界见 [角色接口补充](../../kivo-api-docs/CHARACTER_APP_CONTRACT.md)。

- 搜索需要 name；App 与网页同时发送 name 和 character_data_search。只发后者的旧示例在本次线上不能筛选。
- 当前正确拼写为 special_appearance、equipment；攻击属性增加 Chemical、防御属性增加 CompositeArmor。
- 三个表态 UUID 独立，GET / POST interactive/declares/{uuid}；补充 GET / POST interactive/supplementarys/{uuid}。写方法显式认证，预览后用户提交，超时不自动重试；成功信封允许写入 data=null。
- 作者字段大小写保留，空数组和 NPC 模板值按真实契约处理，不推导或补造数据。
- 生产写接口未自动测试；本地替身覆盖认证、过期、切换表态、不确定响应与补充正文。

以下 F1 / F0 是历史核对记录，与本段冲突时以本段及 20 实施同步为准。

# 0.2.0 协议复核补充

2026-09-07 已使用站长授权测试账号完成真实原生登录、权限文章 33 读取、加密会话恢复与登出。此前“未有真实账号样本”仅描述 0.1.0 阶段。凭据、令牌和内部正文不记录。

- 全局搜索：GET search/，keywords、page、page_size、summary_size；data.search_results / max_page；4041 为无结果。
- 权限文章：GET articles/{id} 显式 Authorization Bearer access；只允许已知文章 ID，不向外链携带凭据。
- 学生译名：given_name_cn / skin_cn；技能：character_datas[].skill.ex_skill / passive_skill 的 info[].describe。
- contributor / contact 为公开前端静态内容，原生展示带日期快照。

以下为 F0 原始核实记录，冲突处以上述补充和 [19](../../App项目文档/19-系统体验迭代实施同步.md) 为准。

# API 实查与差异

核对日期：2026-09-06 至 09-07。来源是已有 `kivo-api-docs`、网站公开页面/浏览器正常下载的前端资源，以及少量匿名只读请求。没有读取服务器、站点源码仓库、浏览器账号存储或用户个人文件；没有提交生产登录或修改站内数据。

## 公开内容

基础地址：`https://api.kivo.wiki/api/v1/`。本次响应的版本字段为 `1.0.0-beta.43`，成功信封为 HTTP 成功且 `success=true`、`code=2000`，时间为 Unix 秒。

| 项目 | 核对结果与实现处理 |
| --- | --- |
| 列表尾斜杠 | `/news`、`/articles`、`/bulletins`、`/data/pick_up` 会返回 301 到相同来源的尾斜杠地址；保留 query。共享客户端只跟随同协议/主机/端口跳转，最多 3 次；写请求只接受 307/308 |
| 卡池 | 实际返回 start_date、end_date、students ID 数组，不是旧示例里的单张 banner；按 ID 读取学生并显示名字 |
| 服别 | 已核实 jp、cn 的日程；未核实国际服可用契约，本期明确缺口 |
| 国服总力战 | 样本时间区间已经结束且 banner 为空；不标为进行中，不为其编造标题或图片 |
| 幸运物 | type 可能是 equipment；公开前端存在 `data/equipments/{id}`。设备样本的说明与图标位于 info 数组中 |
| 装备网页路由 | 未发现独立 `/data/equipment/{id}` 路由；不构造它，速览“网站”入口回到物品仓库 |
| 资讯来源 URL | 有 `kivo.wiki/article/83`、`x.com/...` 这种省略协议的字段；按已识别主机补 HTTPS |
| 图片路径 | 支持 `//static.kivo.wiki/...` 与相对路径；空图片显示统一占位，不请求空地址 |
| 第三方图片 | 国服活动图有 yostar 来源；默认仅加载古书馆图片，设置允许第三方后才加载这些图片 |
| 生日 | week 接口返回 ID；按需补学生简要字段，不预载全站角色 |
| 历史今日 | 使用 timeline 的 start_time_start / start_time_end / start_time_sort；本期只取近五年选读，避免首页全量扫描 |
| 空历史列表 | 实际 2021 年当日样本返回 `max_page=0` 且 `timeline=null`，这是有效空列表；仅此明确形状允许 null，其他缺失/类型错误仍报告解析失败 |
| 历史服别 | 实际 `line_type` 可为大写 JP/CN，先统一大小写再映射服别标签；不把原始缩写当正文展示 |
| 公告分页 | `data.bulletin`、`max_page`；同页缓存/网络双发射不视为重复页，跨页去重并检查空页/重复页终止 |
| 列表形状漂移 | 缺少必需数组或数组类型错误显示解析失败，保留旧缓存；新增无关字段兼容 |

## 账号公开协议

观察到的普通账号登录流程如下；它是客户端公开协议复核，**不是服务器维护方正式承诺，也不是生产成功登录实测**。

1. `POST pow/challenge`，JSON `{difficulty:4}`，读取 data.challenge。
2. 从 nonce=0 开始，计算 UTF-8 的 SHA-256(challenge + nonce 的十进制字符串)，找到十六进制前 4 位为 0 的 nonce。
3. 发送 X-PoW-Challenge、X-PoW-Nonce、X-PoW-Client-Time、X-PoW-Signature。时间为秒；签名规则来自公开网页 worker，公共协议盐集中在 `ProofOfWork.kt`，它不是用户或服务器的秘密。
4. `POST auth/login`，JSON `{account,password}`，取 data.refresh_token。
5. `POST auth/token/access`，Authorization Bearer 刷新令牌，取 data.access_token。
6. `GET user/`，Authorization Bearer 访问令牌，读取用户资料。
7. `GET auth/logout` 使用刷新令牌撤销会话。即使离线失败也清除本机凭据，并明确提示无法确认远端撤销。

网页使用业务码 4010 触发访问令牌刷新。本期会话恢复、主动复验会重新获取访问令牌；401 或已知过期码导致本机过期清理。尚没有其他需要自动重放的私有业务请求；以后接入配队/互动时必须统一扩展单次刷新与请求重放，不能各模块独立刷新。

用户资料成功样本无法在无账号条件下获取，当前兼容 id/user_id、nickname/nick_name/user_name、avatar。缺少有效编号时不会制造“已登录”状态，也不会持久化新会话。真实账号验证时，这个映射和业务错误码是优先核对点。

普通登录界面没有额外图形验证码输入；注册/邮件操作可涉及 Geetest，QQ 登录属于网站 OAuth。后两者本期保留网站入口，明确网站和 App 的会话不自动同步。

## 证据与复核方式

- 首页、登录与协议资源的原始匿名下载位于 `tools/audit`，该目录不进入源码版本控制和发行 APK。
- `tools/inspect-public.mjs` 仅离线解析公开脚本的字符串表，不运行完整网页程序。
- `tools/sample-api.mjs` 是有界匿名采样工具；不要在每次构建时运行，也不要扩成全站抓取。
- 样本只用来核实类型和边界。生产 App 不读取这些文件；设备账号测试使用独立测试 APK 中的内存替身。
- 后续真实登录必须由账号持有人手动完成，不在终端或测试报告里放密码/令牌；定位问题只记录脱敏状态和业务码。
