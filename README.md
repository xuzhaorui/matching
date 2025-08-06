# matching
基基于 JDK 17 Vector API + Agrona 无锁队列 + Disruptor 异步事件驱动架构的高性能匹配引擎，支持百万级并发请求实时处理，广泛适用于大型对战游戏、社交推荐、即时组队等场景。通过批量向量化计算、无 GC 通信链路和事件驱动模型，实现 23K QPS、P99 延迟低于 30ms 的性能表现。

---

## 🧭 使用教程：高性能匹配系统部署与压测指南

---

## 第一步：项目说明

本项目是一个基于 Java Vector API、Agrona 无锁队列 与 Disruptor 构建的高性能匹配系统，适用于高并发匹配、推送型应用（如对战游戏、即时组队、社交推荐）。

### 1️⃣ WebSocket 服务端口

* 默认监听：**`8889`**
* 接口路径：`/ws`
* 协议：WebSocket 双向长连接

### 2️⃣ 核心类说明

#### `EnhancedMatchEngine`

> 高性能匹配引擎，承担批量请求处理的核心逻辑。

* ✅ 使用 Agrona `ManyToOneConcurrentArrayQueue` 实现无锁队列
* ✅ 通过 `Reactor Flux.parallel()` 并行处理分桶请求
* ✅ 具备队列长度 & 老年代 GC 阈值检测限流机制
* ✅ 使用 `drainTo` 就地扫描，减少中间对象创建
* ✅ 定时任务调度匹配任务（`scheduleWithFixedDelay`）

#### `VectorizedMatchPipeline`

> 基于 JDK 17 Vector API 的批量向量化匹配逻辑。

* ✅ 采用 SIMD 指令集批量处理匹配逻辑
* ✅ 替代传统循环判断，提高吞吐率
* ✅ 基于 `IntVector` / `FloatVector` 类型构建精度匹配

#### `DisruptorNotificationService`

> 消息通知模块：基于 Disruptor 的异步推送系统。

* ✅ 高性能 RingBuffer 通道，实现生产者-消费者异步通信
* ✅ 单消费线程模型，避免上下文切换与阻塞
* ✅ 支持消息持久化、追踪、重试保障消息可靠性

---

## 第二步：压测大纲

### 🧱 1. 项目打包与运行命令

运行命令：

```bash
java \
  --add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED \
  --add-modules=jdk.incubator.vector \
  --enable-preview \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:TypeProfileWidth=3 \
  -XX:+UseParallelGC \
  -XX:ParallelGCThreads=4 \
  -XX:-UseBiasedLocking \
  -XX:+AlwaysPreTouch \
  -XX:+UseNUMA \
  -XX:+UseLargePages \
  -XX:MaxRAMPercentage=80 \
  -XX:+PerfDisableSharedMem \
  -XX:+UseCompressedOops \
  -XX:-UseCompressedClassPointers \
  -XX:ReservedCodeCacheSize=1024M \
  -jar match-1.jar
```

参数解释：

* `--enable-preview` + `--add-modules`：开启 JDK Vector API 实验模块
* `-XX:+UseParallelGC`：使用并行垃圾回收器，应对吞吐压力
* `-XX:+AlwaysPreTouch`：启动时预热内存页，降低首次延迟
* `-XX:+UseNUMA`：多核架构下分布式内存优化
* `-XX:MaxRAMPercentage=80`：最大内存使用上限配置
* `-XX:+PerfDisableSharedMem`：关闭 perf 数据共享内存，防止监控干扰
* `-XX:ReservedCodeCacheSize=1024M`：提升 JIT 编译缓冲区

### 🧰 2. 安装 K6 工具（Linux）

```bash
sudo apt update
sudo apt install gnupg software-properties-common
curl -s https://dl.k6.io/key.gpg | sudo apt-key add -
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update
sudo apt install k6
```

### 🧪 3. 示例测试脚本（WebSocket 压测）

```javascript
import { WebSocket } from 'k6/experimental/websockets';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

const counters = new Map();

export const options = {
  vus: 3000,
  duration: '150s',
};

export default function () {
  const ws = new WebSocket('ws://192.168.3.235:8889/ws');

  if (!counters.has(__VU)) {
    counters.set(__VU, 0);
  }
  let messageCount = counters.get(__VU);

  ws.onopen = () => {
    console.log('WebSocket connection established');

    setInterval(() => {
      const message = JSON.stringify({
        mode: 'match',
        username: `user_${__VU}_${++messageCount}`,
        score: randomIntBetween(1, 1000),
        matchRange: 2000,
      });
      counters.set(__VU, messageCount);
      ws.send(message);
    }, 100 / 1000);
  };

  ws.onmessage = (message) => {
    console.log(`Received message: ${message.data}`);
    check(message, {
      'Message is valid': (m) => JSON.parse(m.data).score !== undefined,
    });
  };

  ws.onclose = () => {
    console.log('WebSocket connection closed');
  };

  ws.onerror = (error) => {
    console.error('WebSocket error:', error);
  };

  sleep(options.duration);
}
```

### 📤 4. 示例测试结果（第二轮）

| 项目          | 数据                     |
| ----------- | ---------------------- |
| 测试名称        | 第二轮 WebSocket 并发匹配压测   |
| 并发连接数       | 3000                   |
| 发送频率        | 每 0.1 秒 / VU 发送 1000 条 |
| 总压测时长       | 4 分 44 秒               |
| 消息发送总数      | 761,394 条              |
| 服务端处理总数     | 19,768,488 条           |
| 平均每条请求消息处理数 | ≈ 26x                  |
| 最大并发连接      | 3000                   |
| 平均建连延迟      | 1.05 秒                 |
| P90 建连延迟    | 1.77 秒                 |
| P95 建连延迟    | 1.92 秒                 |
| 最大建连延迟      | 4.29 秒                 |
| 发送速率        | ≈ 2677 条/秒             |
| 带宽占用        | ≈ 5.5 MB/s             |

---

## 🧠 尾语：从规则出发，向高效进化

在构建系统架构的过程中，人决定决策，数据决定成败。你看到的每一个技术选型，背后都是对性能瓶颈的主动响应。期望这份教程，不只是让你跑起项目，更帮助你理解“为什么这样做更优”。

如有问题或建议，欢迎 issue 或讨论交流。

