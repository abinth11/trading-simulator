package com.tradingsim.user.controller;

import com.tradingsim.user.dto.AdminUserDtos.AdminUserResponse;
import com.tradingsim.user.dto.AdminUserDtos.AdminUserSummaryResponse;
import com.tradingsim.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> getUsers() {
        return ResponseEntity.ok(userService.getAdminUsers());
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminUserSummaryResponse> getSummary() {
        return ResponseEntity.ok(userService.getAdminUserSummary());
    }
}
