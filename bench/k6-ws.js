import ws from 'k6/ws';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

export const options = {
  vus: 3000,
  duration: '30s',
};

const latency = new Trend('ws_latency');

export default function () {
  const url = __ENV.WS_URL || 'ws://localhost:8080/ws';
  ws.connect(url, {}, function (socket) {
    socket.on('open', function () {
      socket.setInterval(function () {
        const start = Date.now();
        socket.send('user-' + Math.random());
        socket.on('message', function () {
          latency.add(Date.now() - start);
        });
      }, 100);
    });
    socket.on('error', function (e) {
      console.log('WS error: ' + e.error());
    });
    socket.setTimeout(function () {
      socket.close();
    }, 10000);
  });
}
