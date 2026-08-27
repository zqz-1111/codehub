package com.codehub;

import com.codehub.dto.CreateRepoRequest;
import com.codehub.dto.RepoVO;
import com.codehub.service.RepositoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class IdempotentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private RepositoryService repositoryService;

    @AfterEach
    void tearDown() {
        var keys = redisTemplate.keys("idempotent:999:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("测试真实 Controller 端点 @Idempotent：首次创建仓库成功，快速重复点击被切面拦截返回 429")
    void testControllerIdempotency() throws Exception {
        CreateRepoRequest req = new CreateRepoRequest();
        req.setName("my-test-repo");
        req.setDescription("test repo for idempotency");
        req.setVisibility("PUBLIC");

        RepoVO mockVo = new RepoVO();
        mockVo.setId(1001L);
        mockVo.setName("my-test-repo");
        mockVo.setOwnerId(999L);

        Mockito.when(repositoryService.createRepo(eq(999L), any(CreateRepoRequest.class)))
                .thenReturn(mockVo);

        String jsonBody = objectMapper.writeValueAsString(req);

        // 第一次提交：正常创建成功 (code: 200)
        mockMvc.perform(post("/repos")
                        .requestAttr("userId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.name", is("my-test-repo")));

        // 第二次在 5 秒窗口期内重复提交相同参数：切面拦截抛出 429
        mockMvc.perform(post("/repos")
                        .requestAttr("userId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(429)))
                .andExpect(jsonPath("$.message", is("操作过于频繁，请勿重复提交")));
    }

    @Test
    @DisplayName("测试真实 Controller 端点 @Idempotent：业务抛异常时释放幂等 Key，允许立即重试")
    void testControllerIdempotencyReleaseOnFailure() throws Exception {
        CreateRepoRequest req = new CreateRepoRequest();
        req.setName("fail-repo");
        req.setVisibility("PUBLIC");

        // 模拟业务逻辑抛出 RuntimeException
        Mockito.when(repositoryService.createRepo(eq(999L), any(CreateRepoRequest.class)))
                .thenThrow(new RuntimeException("数据库写入超时"));

        String jsonBody = objectMapper.writeValueAsString(req);

        // 第一次提交：业务失败，返回 500
        mockMvc.perform(post("/repos")
                        .requestAttr("userId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(500)));

        // 业务失败后幂等标记已自动释放，第二次重试不会被 429 拦截，而是再次进入业务逻辑
        mockMvc.perform(post("/repos")
                        .requestAttr("userId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(500))); // 再次返回 500（业务异常）而非 429（防重拦截）
    }
}
