package com.in.amas.ingwclient.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
class ClientSessionInfoDTO extends SessionInfoDTO {
    private String senderName;
    private String receiverName;
}
