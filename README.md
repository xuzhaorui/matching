🧭 Usage Guide: High-Performance Matchmaking System Deployment & Stress Test

Step 1: Project Overview
This project is a high-performance matchmaking system built on Java Vector API, Agrona lock-free queues, and Disruptor. It is designed for high concurrency matchmaking and push-type applications such as competitive games, instant team formation, and social recommendations.

1️⃣ WebSocket Server Details
Default listening port: 8889

Endpoint path: /ws

Protocol: WebSocket bidirectional persistent connection

2️⃣ Core Classes Description
EnhancedMatchEngine
High-performance matchmaking engine responsible for batch request processing core logic.

✅ Implements lock-free queue using Agrona's ManyToOneConcurrentArrayQueue

✅ Uses Reactor's Flux.parallel() to handle bucketed requests concurrently

✅ Supports queue length and old-gen GC threshold based flow control

✅ Uses drainTo for in-place scanning, minimizing intermediate object creation

✅ Scheduling matching tasks via scheduleWithFixedDelay

VectorizedMatchPipeline
Batch vectorized matching logic leveraging JDK 17 Vector API.

✅ Processes matching logic using SIMD instruction sets

✅ Replaces traditional looping with vector operations to improve throughput

✅ Uses IntVector and FloatVector types for precision matching

DisruptorNotificationService
Message notification module based on Disruptor asynchronous push system.

✅ High-performance RingBuffer channel implementing producer-consumer async communication

✅ Single consumer thread model to avoid context switches and blocking

✅ Supports message persistence, tracking, and retry mechanisms to ensure reliability

Step 2: Stress Test Outline
🧱 1. Packaging and Running Command

bash
复制
编辑
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
Parameter explanations:

--enable-preview + --add-modules: Enable JDK Vector API experimental module

-XX:+UseParallelGC: Use parallel garbage collector for throughput pressure

-XX:+AlwaysPreTouch: Pre-warm memory pages at startup to reduce first latency

-XX:+UseNUMA: Optimize distributed memory on multi-core architectures

-XX:MaxRAMPercentage=80: Set max RAM usage limit

-XX:+PerfDisableSharedMem: Disable perf shared memory to avoid monitoring interference

-XX:ReservedCodeCacheSize=1024M: Increase JIT compilation buffer size

🧰 2. Install K6 Tool (Linux)

bash
复制
编辑
sudo apt update
sudo apt install gnupg software-properties-common
curl -s https://dl.k6.io/key.gpg | sudo apt-key add -
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update
sudo apt install k6
🧪 3. Sample Test Script (WebSocket Stress Test)

javascript
复制
编辑
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
📤 4. Sample Test Results (Second Round)

Metric	Data
Test Name	Second Round WebSocket Concurrency Match Test
Concurrent Connections	3000
Send Rate	1000 messages per 0.1s per VU
Total Test Duration	4 minutes 44 seconds
Total Messages Sent	761,394
Total Server Processed	19,768,488
Average Process per Request	≈ 26x
Max Concurrent Connections	3000
Average Connection Latency	1.05 seconds
P90 Connection Latency	1.77 seconds
P95 Connection Latency	1.92 seconds
Max Connection Latency	4.29 seconds
Message Send Rate	≈ 2677 messages/second
Bandwidth Usage	≈ 5.5 MB/s

🧠 Closing Remarks: Evolve Toward Efficiency Based on Principles
In building system architecture, decisions are made by people and success is determined by data. Every technical choice here is a proactive response to performance bottlenecks. This guide aims not only to help you run the project but also to deepen your understanding of why these optimizations matter.

If you have questions or suggestions, feel free to open issues or start discussions.
