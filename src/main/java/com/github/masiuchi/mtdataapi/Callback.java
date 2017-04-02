package com.github.masiuchi.mtdataapi;

import org.json.JSONObject;

public interface Callback {
    public void onSuccess(JSONObject response);
    public void onFailure(JSONObject error);
}
