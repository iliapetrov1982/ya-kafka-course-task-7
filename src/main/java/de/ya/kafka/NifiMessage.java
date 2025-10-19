package de.ya.kafka;

public class NifiMessage {
    private String source;
    private String ts;
    private String msg;

    public NifiMessage() {} // нужен для десериализации

    public NifiMessage(String source, String ts, String msg) {
        this.source = source;
        this.ts = ts;
        this.msg = msg;
    }

    public String getSource() { return source; }
    public String getTs() { return ts; }
    public String getMsg() { return msg; }

    public void setSource(String source) { this.source = source; }
    public void setTs(String ts) { this.ts = ts; }
    public void setMsg(String msg) { this.msg = msg; }

    @Override
    public String toString() {
        return "NifiMessage{source='" + source + "', ts='" + ts + "', msg='" + msg + "'}";
    }
}
