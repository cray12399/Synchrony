package com.example.app;

import java.io.Serializable;

/**
 * Class used to contain permission details within PermissionsActivity.
 */
public class Permission implements Serializable {
    // Constructor variables.
    private final String mManifestPermission;
    private final String mDescription;
    private final int mRequestCode;

    public Permission(String manifestPermission, String description, int requestCode) {
        this.mManifestPermission = manifestPermission;
        this.mDescription = description;
        this.mRequestCode = requestCode;
    }

    public String getManifestPermission() {
        return mManifestPermission;
    }

    public String getDescription() {
        return mDescription;
    }

    public int getRequestCode() {
        return mRequestCode;
    }
}
