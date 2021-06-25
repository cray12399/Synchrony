package com.example.app;

import java.io.Serializable;

public class Permission implements Serializable {
    private final String permissionText;
    private final String manifestPermission;
    private final String description;
    private final int requestCode;

    public Permission(String permissionText, String manifestPermission,
                      String description, int requestCode) {
        this.permissionText = permissionText;
        this.manifestPermission = manifestPermission;
        this.description = description;
        this.requestCode = requestCode;
    }

    public String getPermissionText() {
        return permissionText;
    }

    public String getManifestPermission() {
        return manifestPermission;
    }

    public String getDescription() {
        return description;
    }

    public int getRequestCode() {
        return requestCode;
    }
}
