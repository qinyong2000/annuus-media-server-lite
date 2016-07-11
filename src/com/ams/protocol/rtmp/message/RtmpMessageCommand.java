package com.ams.protocol.rtmp.message;

import com.ams.protocol.rtmp.amf.AmfValue;

public class RtmpMessageCommand extends RtmpMessage {
    private String name;
    private int transactionId;
    private AmfValue[] args;

    public RtmpMessageCommand(String name, int transactionId, AmfValue[] args) {
        super(MESSAGE_AMF0_COMMAND);
        this.name = name;
        this.transactionId = transactionId;
        this.args = args;
    }

    public RtmpMessageCommand(String name, int transactionId, AmfValue commandObject, Object... commandParameters) {
      super(MESSAGE_AMF0_COMMAND);
      this.name = name;
      this.transactionId = transactionId;
      this.args = new AmfValue[commandParameters.length + 1];
      this.args[0] = commandObject;
      for(int i = 0; i < commandParameters.length; i++) {
        this.args[i + 1] = new AmfValue(commandParameters[i]);
      }
  }

    public AmfValue[] getArgs() {
        return args;
    }

    public AmfValue getCommandParameters(int index, Object defaultValue) {
      if (args == null) return new AmfValue(defaultValue);
      if (index < -1 || index >= args.length - 1) return new AmfValue(defaultValue);
      return args[index + 1];
  }
    
    public AmfValue getCommandObject() {
        return args[0];
    }

    public int getTransactionId() {
        return transactionId;
    }

    public String getName() {
        return name;
    }
}
