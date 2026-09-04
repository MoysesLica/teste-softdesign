package com.softdesign.validacaocpf.exception;

public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String mensagem) {
        super(mensagem);
    }

}