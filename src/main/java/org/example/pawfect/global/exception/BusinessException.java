package org.example.pawfect.global.exception;

import lombok.Getter;

/** 서비스 계층에서 "예상 가능한 실패"를 알릴 때 던진다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
