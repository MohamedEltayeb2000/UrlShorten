package com.example.shortener.governance;

import com.example.shortener.common.ApiException;
import org.springframework.http.HttpStatus;

public class ForbiddenApprovalException extends ApiException {
    public ForbiddenApprovalException(String message) {
        super(HttpStatus.FORBIDDEN, "APPROVAL_FORBIDDEN", message);
    }
}
