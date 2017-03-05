package com.masiuchi.mtdataapi;

public class BasicAuth {
    public String username = "";
    public String password = "";

    public boolean isSet() {
        return username != null && !username.equals("")
                && password != null && !password.equals("");
    }
}
