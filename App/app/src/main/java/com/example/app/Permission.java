package com.example.app;

import java.io.Serializable;

// Class used to contain permission details within PermissionsActivity
public class Permission implements Serializable {
    private final String manifestPermission;
    private final String description;
    private final int requestCode;

    public Permission(String manifestPermission, String description, int requestCode) {
        this.manifestPermission = manifestPermission;
        this.description = description;
        this.requestCode = requestCode;
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
