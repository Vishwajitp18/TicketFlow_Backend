package com.project.ticketflow.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccessTokenClaims {
    private Long userId;
    private String name;
    private String email;
    private List<String> roles;
    private String jti;
}
