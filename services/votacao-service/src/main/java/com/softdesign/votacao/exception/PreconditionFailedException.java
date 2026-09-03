package com.softdesign.votacao.exception;

public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String mensagem) {
        super(mensagem);
    }

}