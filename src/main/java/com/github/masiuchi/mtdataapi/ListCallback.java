package com.github.masiuchi.mtdataapi;

import org.json.JSONArray;
import org.json.JSONObject;

public abstract class ListCallback implements Callback {
    public final void onSuccess(JSONObject response) {
        JSONArray items = response.getJSONArray("items");
        int totalResults = response.getInt("totalResults");

        onSuccess(items, totalResults);
    }

    public abstract void onSuccess(JSONArray items, int totalResults);
}
