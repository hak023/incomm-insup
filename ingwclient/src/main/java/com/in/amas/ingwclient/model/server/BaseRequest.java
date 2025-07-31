package com.in.amas.ingwclient.model.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@AllArgsConstructor
@SuperBuilder
public abstract class BaseRequest {
    protected String cmd;
    protected String reqNo;
}