import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const productListDuration = new Trend('product_list_duration');

export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '30s', target: 100 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.1'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 상품 목록 조회
  const res = http.get(`${BASE_URL}/api/products`);
  
  // 응답 시간 기록
  productListDuration.add(res.timings.duration);
  
  // 검증
  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 200ms': (r) => r.timings.duration < 200,
    'has body': (r) => r.body && r.body.length > 0,
  });
  
  errorRate.add(!success);
  
  sleep(0.1);  // 100ms 대기
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'k6/product-list-result.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, opts) {
  const metrics = data.metrics;
  return `
=====================================
상품 목록 조회 API 부하테스트 결과
=====================================

총 요청 수: ${metrics.http_reqs.values.count}
성공률: ${((1 - metrics.errors.values.rate) * 100).toFixed(2)}%

응답 시간:
  - 평균: ${metrics.http_req_duration.values.avg.toFixed(2)}ms
  - 최소: ${metrics.http_req_duration.values.min.toFixed(2)}ms
  - 최대: ${metrics.http_req_duration.values.max.toFixed(2)}ms
  - p(90): ${metrics.http_req_duration.values['p(90)'].toFixed(2)}ms
  - p(95): ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms

TPS: ${(metrics.http_reqs.values.count / data.state.testRunDurationMs * 1000).toFixed(2)}
=====================================
`;
}
