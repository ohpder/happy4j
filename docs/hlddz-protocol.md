# hlddz.apk 脱机协议分析与 Java 实现

## 样本与运行时

- APK：`apk/hlddz.apk`
- SHA-256：`5eb499d699bfb8837374dbd7c348d96d7e9f72cf5cbc27911a3eacd5d9e5719a`
- 应用版本：`8.056.016`，Build `47954`
- Unity：`2020.3.25f1`，IL2CPP metadata v27
- `global-metadata.dat`：17,529,980 bytes，19,153 个类型、88,312 个方法
- `libil2cpp.so`：108,430,232 bytes
- 手工恢复的注册表 VA：`CodeRegistration=0x62c43b8`，`MetadataRegistration=0x62c6570`

Java/Dex 只负责 Android、MSDK 和 Unity 启动层。游戏消息定义位于 IL2CPP 的
`HappyGameDDZ.dll`，Lotus TCP 封包与 TDR/PB 双编解码位于
`libTSDK4CSharp-il2cpp.so`。

## 线格式证据

### CMsgHead（14 bytes，大端）

`TSDK::CMsgHead::Encode` 位于 `0x2259b4`，按以下顺序调用大端
`CEncodeStream`：

| 偏移 | 类型 | 字段 |
|---:|---|---|
| 0 | u16 | message id |
| 2 | i16 | message type：0 request、1 response、2 notify、3 ack、4 internal |
| 4 | i32 | sequence |
| 8 | u8 | source functional entity |
| 9 | u8 | destination functional entity |
| 10 | i16 | source id |
| 12 | i16 | destination id |

对应 Java：`CMsgHeader`。

### CCSHead（16 + ST bytes，大端）

`TSDK::CCSHead::Encode` 位于 `0x711c0c`；`SetUsePB` 位于 `0x711e14`，
将 protocol version 写为十进制 `23`；`CCSPacket::Encode` 位于 `0x7122a4`，
在整个包生成后回填开头的 u16 总长度。

| 偏移 | 类型 | 字段 |
|---:|---|---|
| 0 | u16 | 整包长度（包含 CCSHead） |
| 2 | i16 | protocol version，Lotus TDR 为 0，PB 为 23 |
| 4 | i32 | transport sequence；Lotus `AutoFillCSHead` 写 -1 |
| 8 | i16 | platform selector；账号类型 1/2 均映射为 -3 |
| 10 | u32 | user/game UIN |
| 14 | u8 | flags；认证 Lotus 请求为 `0x11` |
| 15 | u8 | ST length，最大 127 |
| 16 | bytes | SafeLogin 解密后的 `skey`/ST |

flags bit 0 表示 `oi_symmetry_encrypt2`（QQ-TEA）加密；bits 2..3 是压缩号：
0 不压缩、1 LZ4 block、2 zlib、3 保留。解包顺序为 QQ-TEA 解密、解压、
解析 CMsgHead、解析消息体。Lotus 加解密密钥是 SafeLogin 解密后的 `enckey`
前 16 字节；本轮 random key 仅用于解密 SafeLogin 的 `skey/enckey`。对应 Java：`CcsHeader`、`CcsFrameCodec`、
`QqTeaCipher`、`CompressionCodec`。

### CUnknowMsgPara 与 TDR 消息体

`CMsg::Encode` 位于 `0x2263d8`：先写 14-byte CMsgHead，再按 use-PB 标志
选择 `EncodeUsePB`（`0x226268`）或 TDR。托管层 `TSDKServiceProvider.SendMsg(byte[])`
使用 `CUnknowMsgPara_setDecodeBuf(0x2063e4)`，它在业务消息前写入大端 u16 长度，
长度包含自身两个字节。大厅活动的 `ITdrPackUnPack.PBPackTo` 实际生成固定宽度 TDR；
`LotusPayloadCodec` 已覆盖签到、欢乐豆优惠、赛事列表、宝箱领取、QuickGame、AppDir，
以及 GameSvr 登录/坐下/准备/出牌的原生 TDR；其他描述符保留 PB 回退。

### gRPC-over-Lotus 钱包查询

当前客户端在 SafeLogin 与 Lotus 建连完成后调用：

```text
/DDZ3DQueryInfo.DDZ3DQueryInfoService/QueryInfoByBitflag
```

- CMsg ID：`18493`（`TSDK::GRPC_MSGID`），目标 FE：`160`。
- CMsg transport sequence 与 `ControlHeaderReq.seq` 使用两套独立计数器。
- 请求参数的两字节前缀是 `GenericGrpcReq` protobuf 长度；它不同于普通
  `CUnknowMsgPara` 的“长度含前缀”规则。
- `GenericGrpcReq` 字段依次为 `ControlHeaderReq`、完整方法名、业务 body、group name。
- 钱包请求 body 使用 `TargetUin=1`、`TargetAccountType=2`、`TargetClientType=3`、
  `midasInfo=4`、`QueryFlag=17`、`ClientAbility=18`、`ClientVersion=19`、
  `TargetOpenID=30`。当前 `QueryFlag=3` 查询欢乐豆与钻石。
- 响应 `CRspQueryInfoByBitflag.RspData.QueryBean.BeanAmt` 是欢乐豆权威余额。
- GameSvr `15809 type=11` 的本机座位余额变更事件及 GameSo `5/102` 结算都会触发新的
  `QueryInfoByBitflag`，成功后通过 `WALLET` SSE 覆盖页面余额，避免只在登录时查询一次。
- 小游戏 `$ra/bsa` 的资料与公共任务不是普通 HTTP：Java 使用消息 `15502`、目标实体 `155`、
  `CSimpleHttpRequestMsgPara` 原生 TDR 通过 Lotus 转发 CGI。CGI `1028/1029` 获取 QQ/微信
  昵称头像；CGI `32791` 的 `cmd=get/submit` 获取并领取任务。每日欢乐豆补助是任务 `1563`，
  `Status=2` 才显示领取，`PeriodFinishCnt` 作为当天已完成次数，领取后同时刷新任务与 gRPC 余额。

对应实现：`DdzWalletGrpcCodec`、`SocketLotusTransport.grpc`、
`HappyDdzClientService.refreshWallet`。前端通过 `WALLET` SSE 事件立即更新。

## 恢复规模

由 IL2CPP type/field metadata、`*FieldNumber` 常量及 `RepeatedField<T>` 恢复：

- 52 个 `.proto` 文件
- 1,350 个消息
- 253 个枚举
- 原生核心游戏消息 `Native.HuanLeGameSvr.*`：241 个
- 原生统一头 `Native.HLBase.*` / `Native.HLServer.*`：31 个
- 完整 `FileDescriptorSet`：构建时生成到
  `build/generated/resources/main/protocol/hlddz.desc`

Gradle 的 `generateProto` 任务按系统架构从 Maven 下载 `protoc` 并生成强类型 Java 类；
`ProtocolRegistry` 同时提供无需 switch 的 DynamicMessage/JSON 编解码。

原生消息由 Android ARM64 实机探针调用每个类型的 `construct()`/`ToJson()`，再从
`PackToPB` 反汇编恢复字段号、WireFormatLite 类型、重复属性和嵌套回调类型。
188 个原生 message/repeated-message 字段中 184 个已恢复为强类型；剩余 4 个是
selector 决定实际类型的 union value，schema 以 `bytes` 保留完整线数据。

## 完整性组件

- `CcsStreamDecoder`：任意 TCP 分片与粘包
- `GameSoFrameCodec`：原生 game-so u16 长度帧
- `TdrBufferReader` / `TdrBufferWriter`：大端定宽类型、zigzag varint、C 字符串
- `CompressionCodec`：实际 LZ4 match 压缩/解压及 zlib

## 使用

```bash
# 生成 Java 并运行全部验证
./gradlew clean test --no-daemon

# 应用入口启动 Web 客户端
java -jar build/libs/happy4j-0.0.1-SNAPSHOT.jar
```

## 可视化客户端流程

### QQ 扫码 OAuth

原 APK 运行日志确认 QQ App ID `363`、网页登录 App ID `716027609`、DAID `381`。
Java 端保持独立 CookieStore，顺序调用 `xlogin → ptqrshow → ptqrlogin`；`ptqrtoken`
按 QQ 页面算法逐字符计算 `hash += (hash << 5) + char` 并取 `0x7fffffff`。
成功后跟随 `check_sig / graph.qq.com` 跳转，从 `auth://tauth.qq.com` fragment 读取 token，
同时保留 proxy 回调中的 `pay_token/key`。这些 OpenSDK 字段还不是 TSDK 最终票据，Java 会按
APK `MSDKPIXQQ` 的 `MSDK_SCAN_LOGIN` pluginData 结构调用 `/v2/auth/login`，从 QQ channel 2
换取最终的 `token/pf/pf_key`。

MSDK 请求的 `device_info` 字段按 `MSDKUtils::GetDeviceInfoWrapper(0x11954c)` 恢复，包括
`uuid/device_language/app_version/screen_dpi/resolution/trade_mark/manufacturer/model/apn/ram_info/`
`rom_info/cpu_info/imei/qimei/oaid/xid/qimei36`；同一个 Java 进程复用稳定 UUID。
每个 FaaS 请求还必须携带 `seq=<10位>-<10位>-<6位>-<10位>`，位置在 `os` 与 `ts`
之间，并参与 MD5。实测 `/v2/auth/scan_login` 会原样回显该值；省略 `seq` 时服务端虽补发
临时序列并返回 `ret=0`，后续票据并不等价于 APK 的完整请求链。

TSDK 初始化使用游戏枚举 `EnmClientType.Android=64`，该值进入 SafeLogin 的
`clienttype/terminaltype`、Cookie 以及 `emptyreferer`；早期实现误用了模式值 `8`。

### TSDK SafeLogin

- `CLoginTransaction::BuildSafeLoginParm`：`0x2f08b4`
- `CLoginTransaction::RequestQQLogin`：`0x2f909c`
- `CLoginTransaction::EncryptAccess`：`0x2ffb34`
- `CLoginTransaction::GetSafeLoginBodyDataForGCloudOverseas`：`0x2f4458`
- `CLoginTransaction::RequestSafeLoginCgiForGCloudOverseas`：`0x2f4ae4`
- `CLoginTransaction::HandleParamForGCloudOverseas`：`0x2f5084`
- `CLoginTransaction::OnRspSafeLoginCgi`：`0x301e08`

旧分支 `BuildSafeLoginParm` 的明文是：

```text
a1=<openid>&a2=<access_token>&a3=<16-byte random key>
```

国内默认分支使用 RSA/JSON。`COpensslRSA::PublicEncrypt` 在 `0x325a0c` 以
`RSA_size` 作为输入长度并传入 `RSA_NO_PADDING(3)`：明文从 256 字节块首部开始，尾部补零；
这与常见的 PKCS#1 v1.5 不同。CGI 还要求 APK 生成的 referer：

```text
http://emptyreferer.qqgame.qq.com/client/<gameid>/<clienttype>/0
```

缺少 referer 时 Apache 返回 HTTP 403。`a0` 加密 `a1/a2/a3`，`b0` 加密空 custom-info；
响应 `skey/enckey` 由 `SymmetricDecBase64(0x32610c)` 以本轮 random key 做 QQ-TEA 解密。

海外可选分支使用 `application/x-www-form-urlencoded`，字段顺序为：

```text
clienttype=<clientType>&accounttype=<1微信/2QQ>&clientversion=<version>&gameid=<gameId>&openid=<openid>&token=<access_token>
```

路径为 `/cgi-bin/CommonMobileCGI/Common_SafeLogin_GCloud`；QQ 与微信分别使用各自登录域名。
响应关键字段为 `result, resultstr, uin, skey, enckey, webskey, custom`。`skey` 与 `enckey`
均按原生 `HandleParamForGCloudOverseas` 做 Base64 解码。可通过
`HAPPY4J_SAFE_LOGIN_PROTOCOL=gcloud` 显式切换；默认值是 APK 当前国内配置的 `legacy`。

### 大厅与牌桌消息

原生调用点直接给出下列消息号和功能实体：

| 动作 | 消息号 | 目的 FE | 消息体 |
|---|---:|---:|---|
| 快速匹配房间 | 6100 | 61 | `Tdrpb.AppQuickGameSvr.CReqRecommandRoom` |
| AppDir GameSvrList | 10401 | 104 | `Native.AppDir.CReqAppDirStuffPara` |
| BookRouter | 7601 | 1 | `BookRouter.CReqBookRoutePara`（TDR） |
| 登录游戏房间 | 15801 | 1 | `Native.HuanLeGameSvr.CRequestLoginHGPara`（TDR） |
| 坐下 | 15803 | 1 | `Native.HuanLeGameSvr.CRequestSitDownHGPara`（TDR） |
| 准备 | 15810 | 1 | `Native.HuanLeGameSvr.CRequestReadyHGPara`（TDR） |
| GameSo 操作（叫抢/加倍/出牌） | 15813 | 1 | `Native.HuanLeGameSvr.CRequestPlayHGPara`；`PlayData` 为 `Pm/uJa` TDR union |

其中 `CQuickSvrCommand::RequestQuickGame` 位于 `0x720ed4`，`RequestSitDown` 位于
`0x2c6bc0`，`RequestReady` 位于 `0x2c7040`，`SendGameSoMsgPB` 位于 `0x2c9f10`。
这些消息已在 `HappyDdzClientService` 中串成一条状态机，并通过 SSE 将服务器响应推送到页面。

`wxd0e404d795ea6f80_unpacked/src/GameBase/game.js` 的普通快速匹配调用为
`qAa(3, roomId, -1, -1, [])`：`SitDownFlag=3`，桌号/座位均为 `-1`，TLV 列表为空。
Unity/3D 客户端的 `20012 ClientTransparentInfo3D` 与 `20017 ClientAbility` 属于另一条
适配路径。旧 Java 实现把这两个 protobuf union 的字节直接塞进 native TDR
`TTLVUNIT`，GameSvr 因而只继续响应 `15812` 心跳而不返回 `15803`，最终由本地
`orTimeout` 抛出 `TimeoutException`。微信小游戏路径现已恢复为 12-byte TDR：
`000000030000ffffffff0000`。

### 15814 批量封套与对局恢复

微信小游戏的 `Wqa` 以 `CNotifyCallPlayer` 首个 i16 为循环次数。线上坐下成功响应实测
为 `messageCount=15`，依次含 `15803`、`15810`、多条 `15809/15815`。旧实现把该字段
误命名为 `notifyType` 并仅接受值 `1`，因此会丢掉整批牌桌初始化消息。当前通道按
`u16 messageLength + CMsgHead + CUnknowMsgPara` 循环拆出所有消息。

`CRspRecommandRoom` 的 `isLocked/lockSvrId/lockRoomId` 现在参与路由；已锁定时登录现有
GameSvr 后跳过重复坐下。即使锁状态尚未传播、`15803` 返回 `11007`，客户端也会切换
到“正在恢复牌局”并继续接收批量状态，而不是退回失败状态。

入场错误也按 APK 行为分流：`10621/10622` 是安全验证门槛，其中 `10621` 的
`ResultStr` 是一次性 HTTPS 验证地址；UI 只接受 `jiazhang.qq.com` 后展示“前往验证”。
QuickGame `-4` 表示该场次当前动态不可用；UI 冷却 30 秒，不再用静态
`michallExtranet.xml` 的 `isEnable` 连续提交，也不会永久锁死稍后恢复的场次。

## Java 包职责

线协议实现位于 `cc.superbaby.happy4j.protocol.wire`，Descriptor 位于
`cc.superbaby.happy4j.protocol.schema`；应用代码只依赖 `application.port`。
`SocketLotusTransport` 负责大厅 Lotus 与 AppDir；`SocketGameServerChannel` 负责 TGW/BookRouter、
独立 GameSvr 序列号、future 相关性、超时、分片、解密和推送分流。GameSvr 首包 outer sequence
为 `-1`，登录响应的 `PlayerID` 会成为后续 outer sequence；
`ClientSession` 是唯一会话状态所有者，控制器不保存协议状态。
