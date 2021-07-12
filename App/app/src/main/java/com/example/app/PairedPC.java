package com.example.app;

public class PairedPC {
    private final String PC_NAME;
    private final String PC_ADDRESS;
    private boolean isActive;
    private boolean isConnected;

    public PairedPC(String pcName, String pcAddress, boolean isConnected) {
        this.PC_NAME = pcName;
        this.PC_ADDRESS = pcAddress;
        this.isConnected = isConnected;
    }

    public String getName() {
        return PC_NAME;
    }

    public String getAddress() {
        return PC_ADDRESS;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isConnected() {return isConnected;}

    public void setActive(boolean activeDevice) {
        isActive = activeDevice;
    }

    public void setConnected(boolean connected) {isConnected = connected;}
}
