package com.example.app;

public class ConnectedPC {
    private String name;
    private boolean isActivePC;
    private String address;

    public ConnectedPC(String name, String address, boolean isActiveDevice) {
        this.name = name;
        this.address = address;
        this.isActivePC = isActiveDevice;
    }

    public String getName() {
        return name;
    }

    public boolean isActivePC() {
        return isActivePC;
    }

    public void setActivePC(boolean activeDevice) {
        isActivePC = activeDevice;
    }

    public String getAddress() {
        return address;
    }
}
