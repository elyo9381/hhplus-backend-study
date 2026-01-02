package kr.hhplus.be.server.infrastructure.outbox.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 설정
 * 
 * 설정 항목:
 * - Connection Timeout: 5초
 * - Read Timeout: 10초
 * - Write Timeout: 10초
 * - Response Timeout: 10초
 * - Connection Pool: 최대 100개
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                // Connection Timeout
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                // Response Timeout
                .responseTimeout(Duration.ofSeconds(10))
                // Read/Write Timeout
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
