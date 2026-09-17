package com.dp.dto;

import lombok.Data;

/**
 * 登录参数DTO
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
