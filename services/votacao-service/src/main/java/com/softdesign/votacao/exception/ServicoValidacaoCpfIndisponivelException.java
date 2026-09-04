package com.softdesign.votacao.exception;

public class ServicoValidacaoCpfIndisponivelException extends RuntimeException {

    public ServicoValidacaoCpfIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

}
