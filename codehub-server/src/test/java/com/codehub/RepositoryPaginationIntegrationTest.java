package com.codehub;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codehub.entity.Repository;
import com.codehub.entity.User;
import com.codehub.mapper.RepositoryMapper;
import com.codehub.mapper.UserMapper;
import com.codehub.service.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL pagination, all inserted rows roll back after each test. */
@SpringBootTest
@Transactional
class RepositoryPaginationIntegrationTest {
    @Autowired private RepositoryMapper repositories;
    @Autowired private UserMapper users;
    @Autowired private RepositoryService service;

    @Test
    void ownedPagesHaveAccurateTotalsStableTiesAndNoOverlaps() {
        User user = user();
        for (int i = 0; i < 5; i++) repository(user.getId(), "repo-" + i, "PRIVATE");
        var seen = new HashSet<Long>();
        for (int page = 1; page <= 3; page++) {
            var result = service.listMyRepos(user.getId(), page, 2);
            assertEquals(5L, result.getTotal());
            assertEquals(3L, result.getPages());
            assertEquals(page == 3 ? 1 : 2, result.getRecords().size());
            result.getRecords().forEach(repo -> assertTrue(seen.add(repo.getId())));
        }
        assertEquals(5, seen.size());
    }

    @Test
    void publicTotalAndSqlLimitAreBothApplied() {
        long before = repositories.selectCount(new LambdaQueryWrapper<Repository>().eq(Repository::getVisibility, "PUBLIC"));
        User user = user();
        repository(user.getId(), "public-a", "PUBLIC");
        repository(user.getId(), "public-b", "PUBLIC");
        repository(user.getId(), "private-c", "PRIVATE");
        var page = service.listPublicRepos(1, 1);
        assertEquals(before + 2, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("PUBLIC", page.getRecords().get(0).getVisibility());
    }

    private User user() {
        User user = new User();
        user.setUsername("page-" + UUID.randomUUID().toString());
        user.setPassword("test-only-not-a-login-password");
        user.setRole("USER"); user.setStatus("ACTIVE");
        users.insert(user);
        return user;
    }

    private void repository(Long owner, String name, String visibility) {
        Repository repo = new Repository();
        repo.setOwnerId(owner); repo.setName(name); repo.setVisibility(visibility);
        repo.setDefaultBranch("main"); repo.setStarCount(0);
        repo.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        repositories.insert(repo);
    }
}
