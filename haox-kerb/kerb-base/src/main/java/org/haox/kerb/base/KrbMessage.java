package org.haox.kerb.base;

public abstract class KrbMessage {
    private final int pvno = KrbConstant.KERBEROS_V5;
    private KrbMessageType msgType;

    public KrbMessage(KrbMessageType msgType) {
        this.msgType = msgType;
    }

    public int getPvno() {
        return pvno;
    }

    public KrbMessageType getMsgType() {
        return msgType;
    }

    public void setMsgType(KrbMessageType msgType) {
        this.msgType = msgType;
    }
}
