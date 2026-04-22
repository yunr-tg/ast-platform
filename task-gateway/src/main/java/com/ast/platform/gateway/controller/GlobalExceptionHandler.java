package com.ast.platform.gateway.controller;

import com.ast.platform.common.exception.BusinessException;
import com.ast.platform.common.response.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<BaseResponse<Void>> handleValidationException(Exception ex) {
        return ResponseEntity.badRequest().body(BaseResponse.failure("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(BaseResponse.failure(ex.getErrorCode().name(), ex.getMessage()));
    }
}
