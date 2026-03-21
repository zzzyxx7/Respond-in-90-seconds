package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.LoginRequest;
import com.fusion.docfusion.dto.LoginResponse;
import com.fusion.docfusion.dto.RegisterRequest;

public interface AuthService {

    Result<LoginResponse> login(LoginRequest request);

    /**
     * 注册普通用户（role 默认为 USER）。
     */
    Result<LoginResponse> register(RegisterRequest request);
}

