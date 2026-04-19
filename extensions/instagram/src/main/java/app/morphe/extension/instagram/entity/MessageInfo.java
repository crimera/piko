/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.entity;


public class MessageInfo extends Entity {
    private final Object obj;

    public MessageInfo(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public String getMessageType() throws Exception {
        Entity messageTypeDetailEntity = super.getFieldAsEntity("A01");
        return (String) messageTypeDetailEntity.getField("A00");
    }
}
