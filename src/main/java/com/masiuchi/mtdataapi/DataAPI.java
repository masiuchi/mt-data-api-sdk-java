package com.masiuchi.mtdataapi;

import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class DataAPI {
    private static final JSONObject ERROR_JSON = new JSONObject();
    static {
        ERROR_JSON.put("code", "-1");
        ERROR_JSON.put("message", "The operation has not been completed.");
    }

    //MARK: - Properties
    public String token = "";
    public String sessionID = "";

    public String endpointVersion = "v3";
    public String APIBaseURL = "";

    public String clientID = "MTDataAPIClient";

    public BasicAuth basicAuth = new BasicAuth();

    public String apiVersion = "";
    public OkHttpClient httpClient = new OkHttpClient();

    //MARK: - Methods
    public DataAPI(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.equals("")) {
            throw new IllegalArgumentException();
        }
        APIBaseURL = apiBaseUrl;
    }

    private String APIURL() {
        return APIBaseURL + "/" + endpointVersion;
    }

    private String APIURL_V2() {
        return APIBaseURL + "/v2";
    }

    private void resetAuth() {
        token = "";
        sessionID = "";
    }

    private Request makeRequest(HttpMethod method, String url, Parameter parameters, boolean useSession) {
        Request.Builder requestBuilder = new Request.Builder();

        if (!token.equals("")) {
            requestBuilder.addHeader("X-MT-Authorization", "MTAuth accessToken=" + token);
        } else if (useSession && !sessionID.equals("")) {
            requestBuilder.addHeader("X-MT-Authorization", "MTAuth sessionId=" + sessionID);
        }

        if (method == HttpMethod.GET) {
            HttpUrl httpUrl = HttpUrl.parse(url);

            HttpUrl.Builder httpUrlBuilder = new HttpUrl.Builder();
            httpUrlBuilder
                    .scheme(httpUrl.scheme())
                    .host(httpUrl.host())
                    .port(httpUrl.port());

            for (String s : httpUrl.pathSegments()) {
                httpUrlBuilder.addPathSegment(s);
            }
            if (parameters != null) {
                for (String key : parameters.keySet()) {
                    httpUrlBuilder.addQueryParameter(key, parameters.get(key).toString());
                }
            }

            requestBuilder
                    .url(httpUrlBuilder.build())
                    .get();
        } else {
            try {
                requestBuilder.url(new URL(url));
            } catch (MalformedURLException e) {
                return null;
            }

            FormBody.Builder formBodyBuilder = new FormBody.Builder();
            if (parameters != null) {
                for (String key : parameters.keySet()) {
                    formBodyBuilder.addEncoded(key, parameters.get(key).toString());
                }
            }
            FormBody body = formBodyBuilder.build();
            requestBuilder.method(method.name(), body);
        }

        if (basicAuth.isSet()) {
            String credential = Credentials.basic(basicAuth.username, basicAuth.password);
            requestBuilder.header("Authorization", credential);
        }

        return requestBuilder.build();
    }

    private void actionCommon(HttpMethod action, String url, Parameter params, Callback callback) {
        Request request = makeRequest(action, url, params, false);

        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        if (response == null || !response.isSuccessful()) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        String body;
        try {
            body = response.body().string();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        JSONObject json = new JSONObject(body);
        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                callback.onFailure(error);
            }
            return;
        }

        callback.onSuccess(json);
    }

    private void action(String name, HttpMethod action, String url, Resource obj, Parameter options, Callback callback) {
        if (obj != null) {
            if (options == null) {
                options = new Parameter();
            }
            JSONObject json = new JSONObject(obj);
            options.put(name, json.toString());
        }
        actionCommon(action, url, options, callback);
    }

    private void get(String url, Parameter params, Callback callback) {
        actionCommon(HttpMethod.GET, url, params, callback);
    }

    private void post(String url, Parameter params, ActionCallback callback) {
        actionCommon(HttpMethod.POST, url, params, callback);
    }

    private void put(String url, Parameter params, ActionCallback callback) {
        actionCommon(HttpMethod.PUT, url, params, callback);
    }

    private void delete(String url, Parameter params, ActionCallback callback) {
        actionCommon(HttpMethod.DELETE, url, params, callback);
    }

    private void repeatAction(HttpMethod action, String url, Parameter options, final Callback callback) {
        Request request = makeRequest(action, url, options, false);

        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        if (response == null || !response.isSuccessful()) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        String body;
        try {
            body = response.body().string();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        JSONObject json = new JSONObject(body);
        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                callback.onFailure(error);
            }
            return;
        }

        if (json.has("status") && json.getString("status").equals("Complete")) {
            callback.onSuccess(json);
            return;
        }
        if (json.has("restIds") && json.getString("restIds").equals("")) {
            callback.onSuccess(json);
            return;
        }

        String nextURL = response.header("X-MT-Next-Phase-URL");
        if (nextURL != null && !nextURL.equals("")) {
            String next = APIURL() + "/" + nextURL;
            repeatAction(action, next, options, callback);
        } else {
            callback.onFailure(ERROR_JSON);
        }
    }

    private void upload(byte[] data, String fileName, String url, Parameter parameters, ActionCallback callback) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName,
                        RequestBody.create(MediaType.parse("application/octet-stream"), data)
                );

        if (parameters != null) {
            for (String key : parameters.keySet()) {
                bodyBuilder.addFormDataPart(key, parameters.get(key).toString());
            }
        }
        RequestBody requestBody = bodyBuilder.build();

        Request.Builder requestBuilder = new Request.Builder();
        if (token != null && !token.equals("")) {
            requestBuilder.header("X-MT-Authorization", "MTAuth accessToken=" + token);
        }
        Request request = requestBuilder.url(url)
                .post(requestBody)
                .build();

        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        if (response == null || !response.isSuccessful()) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        String body;
        try {
            body = response.body().string();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        JSONObject json = new JSONObject(body);
        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                callback.onFailure(error);
            }
            return;
        }

        callback.onSuccess(json);
    }

    //MARK: - APIs

    //MARK: - # V2
    //MARK: - System
    public void listEndpoints(Parameter options, Callback callback) {
        String url = APIURL() + "/endpoints";
        get(url, options, callback);
    }

    private void authenticationCommon(String url, String username, String password, boolean remember, final ActionCallback callback) {
        resetAuth();

        Parameter params = new Parameter();
        params.put("username", username);
        params.put("password", password);
        params.put("remember", remember ? "1" : "0");
        params.put("clientId", clientID);

        post(url, params, new ActionCallback() {
            public void onSuccess(JSONObject response) {
                if (response.has("accessToken")) {
                    token = response.getString("accessToken");
                }
                if (response.has("sessionId")) {
                    sessionID = response.getString("sessionId");
                }

                callback.onSuccess(response);
            }

            public void onFailure(JSONObject error) {
                callback.onFailure(error);
            }
        });
    }

    public void authenticate(String username, String password, boolean remember, ActionCallback callback) {
        String url = APIURL() + "/authentication";
        authenticationCommon(url, username, password, remember, callback);
    }

    public void authenticateV2(String username, String password, boolean remember, ActionCallback callback) {
        String url = APIURL_V2() + "/authentication";
        authenticationCommon(url, username, password, remember, callback);
    }

    public void getToken(final ActionCallback callback) {
        if (sessionID != null && sessionID.equals("")) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        String url = APIURL() + "/token";

        post(url, null, new ActionCallback() {
            public void onSuccess(JSONObject response) {
                if (response.has("accessToken")) {
                    token = response.getString("accessToken");
                }

                callback.onSuccess(response);
            }

            public void onFailure(JSONObject error) {
                callback.onFailure(error);
            }
        });
    }

    public void revokeAuthentication(final ActionCallback callback) {
        if (sessionID != null && sessionID.equals("")) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        String url = APIURL() + "/authentication";

        delete(url, null, new ActionCallback() {
            public void onSuccess(JSONObject response) {
                sessionID = "";

                callback.onSuccess(response);
            }

            public void onFailure(JSONObject error) {
                callback.onFailure(error);
            }
        });
    }

    public void revokeToken(final ActionCallback callback) {
        String url = APIURL() + "/token";
        delete(url, null, new ActionCallback() {
            public void onSuccess(JSONObject response) {
                token = "";

                callback.onSuccess(response);
            }

            public void onFailure(JSONObject error) {
                callback.onFailure(error);
            }
        });
    }

    //MARK: - Search
    public void search(String query, Parameter options, Callback callback) {
        String url = APIURL() + "/search";

        if (options == null) {
            options = new Parameter();
        }
        options.put("search", query);

        get(url, options, callback);
    }

    //MARK: - Site
    public void listSites(Parameter options, Callback callback) {
        String url = APIURL() + "/sites";
        get(url, options, callback);
    }

    public void listSitesByParent(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/children";
        get(url, options, callback);
    }

    private void siteAction(HttpMethod action, String siteID, Resource site, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites";
        if (action != HttpMethod.POST) {
            if (siteID != null && !siteID.equals("")) {
                url += "/" + siteID;
            }
        }
        action("website", action, url, site, options, callback);
    }

    public void insertNewWebsite(Resource site, Parameter options, ActionCallback callback) {
        siteAction(HttpMethod.POST, null, site, options, callback);
    }

    public void createSite(Resource site, Parameter options, ActionCallback callback) {
        insertNewWebsite(site, options, callback);
    }

    public void getWebsite(String siteID, Parameter options, ActionCallback callback) {
        siteAction(HttpMethod.GET, siteID, null, options, callback);
    }

    public void getSite(String siteID, Parameter options, ActionCallback callback) {
        getWebsite(siteID, options, callback);
    }

    public void updateSite(String siteID, Resource site, Parameter options, ActionCallback callback) {
        siteAction(HttpMethod.PUT, siteID, site, options, callback);
    }

    public void deleteSite(String siteID, Parameter options, ActionCallback callback) {
        siteAction(HttpMethod.DELETE, siteID, null, options, callback);
    }

    public void backupSite(String siteID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/backup";
        get(url, options, callback);
    }

    //MARK: - Blog
    public void listBlogsForUser(String userID, Parameter options, Callback callback) {
        String url = APIURL() + "/users/" + userID + "/sites";
        get(url, options, callback);
    }

    private void blogAction(HttpMethod action, String blogID, Resource blog, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites";
        if (blogID != null && !blogID.equals("")) {
            url += "/" + blogID;
        }
        action("blog", action, url, blog, options, callback);
    }

    public void insertNewBlog(Resource blog, Parameter options, ActionCallback callback) {
        blogAction(HttpMethod.POST, null, blog, options, callback);
    }

    public void createBlog(Resource blog, Parameter options, ActionCallback callback) {
        insertNewBlog(blog, options, callback);
    }

    public void getBlog(String blogID, Parameter options, ActionCallback callback) {
        blogAction(HttpMethod.GET, blogID, null, options, callback);
    }

    public void updateBlog(String blogID, Resource blog, Parameter options, ActionCallback callback) {
        blogAction(HttpMethod.PUT, blogID, blog, options, callback);
    }

    public void deleteBlog(String blogID, Parameter options, ActionCallback callback) {
        blogAction(HttpMethod.DELETE, blogID, null, options, callback);
    }

    //MARK: - Entry
    public void listEntries(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/entries";
        get(url, options, callback);
    }

    private void entryAction(HttpMethod action, String siteID, String entryID, Resource entry, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/entries";
        if (action != HttpMethod.POST) {
            if (entryID != null && !entryID.equals("")) {
                url += "/" + entryID;
            }
        }
        action("entry", action, url, entry, options, callback);
    }

    public void createEntry(String siteID, Resource entry, Parameter options, ActionCallback callback) {
        entryAction(HttpMethod.POST, siteID, null, entry, options, callback);
    }

    public void getEntry(String siteID, String entryID, Parameter options, ActionCallback callback) {
        entryAction(HttpMethod.GET, siteID, entryID, null, options, callback);
    }

    public void updateEntry(String siteID, String entryID, Resource entry, Parameter options, ActionCallback callback) {
        entryAction(HttpMethod.PUT, siteID, entryID, entry, options, callback);
    }

    public void deleteEntry(String siteID, String entryID, Parameter options, ActionCallback callback) {
        entryAction(HttpMethod.DELETE, siteID, entryID, null, options, callback);
    }

    private void listEntriesForObject(String objectName, String siteID, String objectID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/" + objectName + "/" + objectID + "/entries";
        get(url, options, callback);
    }

    public void listEntriesForCategory(String siteID, String categoryID, Parameter options, Callback callback) {
        listEntriesForObject("categories", siteID, categoryID, options, callback);
    }

    public void listEntriesForAsset(String siteID, String assetID, Parameter options, Callback callback) {
        listEntriesForObject("assets", siteID, assetID, options, callback);
    }

    public void listEntriesForSiteAndTag(String siteID, String tagID, Parameter options, Callback callback) {
        listEntriesForObject("tags", siteID, tagID, options, callback);
    }

    public void exportEntries(String siteID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/entries/export";

        Request request = makeRequest(HttpMethod.GET, url, options, false);

        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        if (response == null || !response.isSuccessful()) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        String body;
        try {
            body = response.body().string();
        } catch (IOException e) {
            callback.onFailure(ERROR_JSON);
            return;
        }

        JSONObject json = new JSONObject(body);
        if (json.has("error")) {
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                callback.onFailure(error);
            }
            return;
        }

        callback.onSuccess(json);
    }

    public void publishEntries(String[] entryIDs, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/publish/entries";

        StringBuffer buffer = new StringBuffer();
        for (int i = 0 ; i < entryIDs.length; i++) {
            if (i > 0) {
                buffer.append(",");
            }
            buffer.append(entryIDs[i]);
        }
        String ids = buffer.toString();

        if (options == null) {
            options = new Parameter();
        }
        options.put("ids", ids);

        repeatAction(HttpMethod.GET, url, options, callback);
    }

    public void importEntriesWithFile(String siteID, byte[] importData, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/entries/import";
        upload(importData, "import.dat", url, options, callback);
    }

    public void importEntries(String siteID, byte[] importData, Parameter options, ActionCallback callback) {
        if (importData != null && importData.length > 0) {
            importEntriesWithFile(siteID, importData, options, callback);
            return;
        }
        String url = APIURL() + "/sites/" + siteID + "/entries/import";
        post(url, options, callback);
    }

    public void previewEntryById(String siteID, String entryID, Resource entry, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/entries/" + entryID + "/preview";
        action("entry", HttpMethod.POST, url, entry, options, callback);
    }

    public void previewEntry(String siteID, Resource entry, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/entries/preview";
        action("entry", HttpMethod.POST, url, entry, options, callback);
    }

    //MARK: - Page
    public void listPages(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/pages";
        get(url, options, callback);
    }

    private void pageAction(HttpMethod action, String siteID, String pageID, Resource page, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/pages";
        if (action != HttpMethod.POST) {
            if (pageID != null && !pageID.equals("")) {
                url += "/" + pageID;
            }
        }
        action("page", action, url, page, options, callback);
    }

    public void createPage(String siteID, Resource page, Parameter options, ActionCallback callback) {
        pageAction(HttpMethod.POST, siteID, null, page, options, callback);
    }

    public void getPage(String siteID, String pageID, Parameter options, ActionCallback callback) {
        pageAction(HttpMethod.GET, siteID, pageID, null, options, callback);
    }

    public void updatePage(String siteID, String pageID, Resource page, Parameter options, ActionCallback callback) {
        pageAction(HttpMethod.PUT, siteID, pageID, page, options, callback);
    }

    public void deletePage(String siteID, String pageID, Parameter options, ActionCallback callback) {
        pageAction(HttpMethod.DELETE, siteID, pageID, null, options, callback);
    }

    private void listPagesForObject(String objectName, String siteID, String objectID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/" + objectName + "/" + objectID + "/pages";
        get(url, options, callback);
    }

    public void listPagesForFolder(String siteID, String folderID, Parameter options, Callback callback) {
        listPagesForObject("folders", siteID, folderID, options, callback);
    }

    public void listPagesForAsset(String siteID, String assetID, Parameter options, Callback callback) {
        listPagesForObject("assets", siteID, assetID, options, callback);
    }

    public void listPagesForSiteAndTag(String siteID, String tagID, Parameter options, Callback callback) {
        listPagesForObject("tags", siteID, tagID, options, callback);
    }

    public void previewPageById(String siteID, String pageID, Resource page, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/pages/" + pageID + "/preview";
        action("page", HttpMethod.POST, url, page, options, callback);
    }

    public void previewPage(String siteID, Resource page, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/pages/preview";
        action("page", HttpMethod.POST, url, page, options, callback);
    }

    //MARK: - Category
    public void listCategories(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/categories";
        get(url, options, callback);
    }

    private void categoryAction(HttpMethod action, String siteID, String categoryID, Resource category, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/categories";
        if (action != HttpMethod.POST) {
            if (categoryID != null && !categoryID.equals("")) {
                url += "/" + categoryID;
            }
        }
        action("category", action, url, category, options, callback);
    }

    public void createCategory(String siteID, Resource category, Parameter options, ActionCallback callback) {
        categoryAction(HttpMethod.POST, siteID, null, category, options, callback);
    }

    public void getCategory(String siteID, String categoryID, Parameter options, ActionCallback callback) {
        categoryAction(HttpMethod.GET, siteID, categoryID, null, options, callback);
    }

    public void updateCategory(String siteID, String categoryID, Resource category, Parameter options, ActionCallback callback) {
        categoryAction(HttpMethod.PUT, siteID, categoryID, category, options, callback);
    }

    public void deleteCategory(String siteID, String categoryID, Parameter options, ActionCallback callback) {
        categoryAction(HttpMethod.DELETE, siteID, categoryID, null, options, callback);
    }

    public void listCategoriesForEntry(String siteID, String entryID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites" + siteID + "/entries/" + entryID + "/categories";
        get(url, options, callback);
    }

    private void listCategoriesForRelation(String relation, String siteID, String categoryID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/categories/" + categoryID + "/" + relation;
        get(url, options, callback);
    }

    public void listParentCategories(String siteID, String categoryID, Parameter options, Callback callback) {
        listCategoriesForRelation("parents", siteID, categoryID, options, callback);
    }

    public void listSiblingCategories(String siteID, String categoryID, Parameter options, Callback callback) {
        listCategoriesForRelation("siblings", siteID, categoryID, options, callback);
    }

    public void listChildCategories(String siteID, String categoryID, Parameter options, Callback callback) {
        listCategoriesForRelation("children", siteID, categoryID, options, callback);
    }

    public void permutateCategories(String siteID, Parameter[] categories, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/categories/permutate";

        if (categories != null && categories.length > 0) {
            if (options == null) {
                options = new Parameter();
            }
            JSONObject json = new JSONObject(categories);
            options.put("categories", json.toString());
        }

        post(url, options, callback);
    }

    //MARK: - Folder
    public void listFolders(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/folders";
        get(url, options, callback);
    }

    private void folderAction(HttpMethod action, String siteID, String folderID, Resource folder, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/folders";
        if (folderID != null && !folderID.equals("")) {
            url += "/" + folderID;
        }
        action("folder", action, url, folder, options, callback);
    }

    public void createFolder(String siteID, Resource folder, Parameter options, ActionCallback callback) {
        folderAction(HttpMethod.POST, siteID, null, folder, options, callback);
    }

    public void getFolder(String siteID, String folderID, Parameter options, ActionCallback callback) {
        folderAction(HttpMethod.GET, siteID, folderID, null, options, callback);
    }

    public void updateFolder(String siteID, String folderID, Resource folder, Parameter options, ActionCallback callback) {
        folderAction(HttpMethod.PUT, siteID, folderID, folder, options, callback);
    }

    public void deleteFolder(String siteID, String folderID, Parameter options, ActionCallback callback) {
        folderAction(HttpMethod.DELETE, siteID, folderID, null, options, callback);
    }

    private void listFoldersForRelation(String relation, String siteID, String folderID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/folders/" + folderID + "/" + relation;
        get(url, options, callback);
    }

    public void listParentFolders(String siteID, String folderID, Parameter options, Callback callback) {
        listFoldersForRelation("parents", siteID, folderID, options, callback);
    }

    public void listSiblingFolders(String siteID, String folderID, Parameter options, Callback callback) {
        listFoldersForRelation("siblings", siteID, folderID, options, callback);
    }

    public void listChildFolders(String siteID, String folderID, Parameter options, Callback callback) {
        listFoldersForRelation("children", siteID, folderID, options, callback);
    }

    public void permutateFolders(String siteID, Parameter[] folders, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/folders/permutate";

        if (folders != null && folders.length > 0) {
            if (options == null) {
                options = new Parameter();
            }
            JSONObject json = new JSONObject(folders);
            options.put("folders", json.toString());
        }

        post(url, options, callback);
    }

    //MARK: - Tag
    public void listTagsForSite(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/tags";
        get(url, options, callback);
    }

    private void tagAction(HttpMethod action, String siteID, String tagID, Resource tag, Parameter options, ActionCallback callback) {
        if (action == HttpMethod.POST) {
            callback.onFailure(ERROR_JSON);
            return;
        }
        String url = APIURL() + "/sites/" + siteID + "/tags/" + tagID;
        action("tag", action, url, tag, options, callback);
    }

    public void getTagForSite(String siteID, String tagID, Parameter options, ActionCallback callback) {
        tagAction(HttpMethod.GET, siteID, tagID, null, options, callback);
    }

    public void renameTagForSite(String siteID, String tagID, Resource tag, Parameter options, ActionCallback callback) {
        tagAction(HttpMethod.PUT, siteID, tagID, tag, options, callback);
    }

    public void deleteTagForSite(String siteID, String tagID, Parameter options, ActionCallback callback) {
        tagAction(HttpMethod.DELETE, siteID, tagID, null, options, callback);
    }

    //MARK: - User
    public void listUsers(Parameter options, Callback callback) {
        String url = APIURL() + "/users";
        get(url, options, callback);
    }

    private void userAction(HttpMethod action, String userID, Resource user, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/users";
        if (action != HttpMethod.POST) {
            if (userID != null && !userID.equals("")) {
                url += "/" + userID;
            }
        }
        action("user", action, userID, user, options, callback);
    }

    public void createUser(Resource user, Parameter options, ActionCallback callback) {
        userAction(HttpMethod.POST, null, user, options, callback);
    }

    public void getUser(String userID, Parameter options, ActionCallback callback) {
        userAction(HttpMethod.GET, userID, null, options, callback);
    }

    public void updateUser(String userID, Resource user, Parameter options, ActionCallback callback) {
        userAction(HttpMethod.PUT, userID, user, options, callback);
    }

    public void deleteUser(String userID, Parameter options, ActionCallback callback) {
        userAction(HttpMethod.DELETE, userID, null, options, callback);
    }

    public void unlockUser(String userID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/users/" + userID + "/unlock";
        post(url, options, callback);
    }

    public void recoverPasswordForUser(String userID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/users/" + userID + "/recover_password";
        post(url, options, callback);
    }

    public void recoverPassword(String name, String email, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/recover_password";

        if (options == null) {
            options = new Parameter();
        }
        options.put("name", name);
        options.put("email", email);

        post(url, options, callback);
    }

    //MARK: - Asset
    public void listAssets(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/assets";
        get(url, options, callback);
    }

    public void uploadAsset(byte[] assetData, String fileName, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/assets/upload";
        upload(assetData, fileName, url, options, callback);
    }

    public void uploadAssetForSite(String siteID, byte[] assetData, String fileName, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/assets/upload";
        upload(assetData, fileName, url, options, callback);
    }

    private void assetAction(HttpMethod action, String siteID, String assetID, Resource asset, Parameter options, ActionCallback callback) {
        if (action == HttpMethod.POST) {
            callback.onFailure(ERROR_JSON);
            return;
        }
        String url = APIURL() + "/sites/" + siteID + "/assets/" + assetID;
        action("asset", action, url, asset, options, callback);
    }

    public void getAsset(String siteID, String assetID, Parameter options, ActionCallback callback) {
        assetAction(HttpMethod.GET, siteID, assetID, null, options, callback);
    }

    public void updateAsset(String siteID, String assetID, Resource asset, Parameter options, ActionCallback callback) {
        assetAction(HttpMethod.PUT, siteID, assetID, asset, options, callback);
    }

    public void deleteAsset(String siteID, String assetID, Parameter asset, Parameter options, ActionCallback callback) {
        assetAction(HttpMethod.DELETE, siteID, assetID, null, options, callback);
    }

    private void listAssetsForObject(String objectName, String siteID, String objectID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/" + objectName + "/" + objectID + "/assets";
        get(url, options, callback);
    }

    public void listAssetsForEntry(String siteID, String entryID, Parameter options, Callback callback) {
        listAssetsForObject("entry", siteID, entryID, options, callback);
    }

    public void listAssetsForPage(String siteID, String pageID, Parameter options, Callback callback) {
        listAssetsForObject("page", siteID, pageID, options, callback);
    }

    public void listAssetsForSiteAndTag(String siteID, String tagID, Parameter options, Callback callback) {
        listAssetsForObject("tag", siteID, tagID, options, callback);
    }

    public void getThumbnail(String siteID, String assetID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/assets/" + assetID + "thumbnail";
        get(url, options, callback);
    }

    //MARK: - Comment
    public void listComments(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/comments";
        get(url, options, callback);
    }

    private void commentAction(HttpMethod action, String siteID, String commentID, Resource comment, Parameter options, ActionCallback callback) {
        if (action == HttpMethod.POST) {
            callback.onFailure(ERROR_JSON);
            return;
        }
        String url = APIURL() + "/sites/" + siteID + "/comments/" + commentID;
        action("comment", action, url, comment, options, callback);
    }

    public void getComment(String siteID, String commentID, Parameter options, ActionCallback callback) {
        commentAction(HttpMethod.GET, siteID, commentID, null, options, callback);
    }

    public void updateComment(String siteID, String commentID, Resource comment, Parameter options, ActionCallback callback) {
        commentAction(HttpMethod.PUT, siteID, commentID, comment, options, callback);
    }

    public void deleteComment(String siteID, String commentID, Parameter options, ActionCallback callback) {
        commentAction(HttpMethod.DELETE, siteID, commentID, null, options, callback);
    }

    private void listCommentsForObject(String objectName, String siteID, String objectID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/" + objectName + "/" + objectID + "/comments";
        get(url, options, callback);
    }

    public void listCommentsForEntry(String siteID, String entryID, Parameter options, Callback callback) {
        listCommentsForObject("entry", siteID, entryID, options, callback);
    }

    public void listCommentsForPage(String siteID, String pageID, Parameter options, Callback callback) {
        listCommentsForObject("page", siteID, pageID, options, callback);
    }

    private void createCommentForObject(String objectName, String siteID, String objectID, Resource comment, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/" + objectName + "/" + objectID + "/comments";
        action("comment", HttpMethod.POST, url, comment, options, callback);
    }

    public void createCommentForEntry(String siteID, String entryID, Resource comment, Parameter options, ActionCallback callback) {
        createCommentForObject("entries", siteID, entryID, comment, options, callback);
    }

    public void createCommentForPage(String siteID, String pageID, Resource comment, Parameter options, ActionCallback callback) {
        createCommentForObject("pages", siteID, pageID, comment, options, callback);
    }

    private void createReplyCommentForObject(String objectName, String siteID, String objectID, String commentID, Resource reply, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/" + objectName + "/" + objectID + "/comments/" + commentID + "/replies";
        action("comment", HttpMethod.POST, url, reply, options, callback);
    }

    public void createReplyCommentForEntry(String siteID, String entryID, String commentID, Resource reply, Parameter options, ActionCallback callback) {
        createReplyCommentForObject("entries", siteID, entryID, commentID, reply, options, callback);
    }

    public void createReplyCommentForPage(String siteID, String pageID, String commentID, Resource reply, Parameter options, ActionCallback callback) {
        createReplyCommentForObject("pages", siteID, pageID, commentID, reply, options, callback);
    }

    //MARK: - Trackback
    public void listTrackbacks(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/trackbacks";
        get(url, options, callback);
    }

    private void trackbackAction(HttpMethod action, String siteID, String trackbackID, Resource trackback, Parameter options, ActionCallback callback) {
        if (action == HttpMethod.POST) {
            callback.onFailure(ERROR_JSON);
            return;
        }
        String url = APIURL() + "/sites/" + siteID + "/trackbacks/" + trackbackID;
        action("trackback", action, url, trackback, options, callback);
    }

    public void getTrackback(String siteID, String trackbackID, Parameter options, ActionCallback callback) {
        trackbackAction(HttpMethod.GET, siteID, trackbackID, null, options, callback);
    }

    public void updateTrackback(String siteID, String trackbackID, Resource trackback, Parameter options, ActionCallback callback) {
        trackbackAction(HttpMethod.PUT, siteID, trackbackID, trackback, options, callback);
    }

    public void deleteTrackbacks(String siteID, String trackbackID, Parameter options, ActionCallback callback) {
        trackbackAction(HttpMethod.DELETE, siteID, trackbackID, null, options, callback);
    }

    private void listTrackbacksForObject(String objectName, String siteID, String objectID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/" + objectName + "/" + objectID + "/trackbacks";
        get(url, options, callback);
    }

    public void listTrackbacksForEntry(String siteID, String entryID, Parameter options, Callback callback) {
        listTrackbacksForObject("entries", siteID, entryID, options, callback);
    }

    public void listTrackbacksForPage(String siteID, String pageID, Parameter options, Callback callback) {
        listTrackbacksForObject("pages", siteID, pageID, options, callback);
    }

    //MARK: - Field
    public void listFields(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/fields";
        get(url, options, callback);
    }

    private void fieldAction(HttpMethod action, String siteID, String fieldID, Resource field, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/fields";
        if (action != HttpMethod.POST) {
            if (fieldID != null && !fieldID.equals("")) {
                url += "/" + fieldID;
            }
        }
        action("field", action, url, field, options, callback);
    }

    public void createField(String siteID, Resource field, Parameter options, ActionCallback callback) {
        fieldAction(HttpMethod.POST, siteID, null, field, options, callback);
    }

    public void getField(String siteID, String fieldID, Parameter options, ActionCallback callback) {
        fieldAction(HttpMethod.GET, siteID, fieldID, null, options, callback);
    }

    public void updateField(String siteID, String fieldID, Resource field, Parameter options, ActionCallback callback) {
        fieldAction(HttpMethod.PUT, siteID, fieldID, field, options, callback);
    }

    public void deleteField(String siteID, String fieldID, Parameter options, ActionCallback callback) {
        fieldAction(HttpMethod.DELETE, siteID, fieldID, null, options, callback);
    }

    //MARK: - Template
    public void listTemplates(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/templates";
        get(url, options, callback);
    }

    private void templateAction(HttpMethod action, String siteID, String templateID, Resource template, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/templates";
        if (action != HttpMethod.POST) {
            if (templateID != null && !templateID.equals("")) {
                url += "/" + templateID;
            }
        }
        action("template", action, url, template, options, callback);
    }

    public void createTemplate(String siteID, Resource template, Parameter options, ActionCallback callback) {
        templateAction(HttpMethod.POST, siteID, null, template, options, callback);
    }

    public void getTemplate(String siteID, String templateID, Parameter options, ActionCallback callback) {
        templateAction(HttpMethod.GET, siteID, templateID, null, options, callback);
    }

    public void updateTemplate(String siteID, String templateID, Resource template, Parameter options, ActionCallback callback) {
        templateAction(HttpMethod.PUT, siteID, templateID, template, options, callback);
    }

    public void deleteTemplate(String siteID, String templateID, Parameter options, ActionCallback callback) {
        templateAction(HttpMethod.DELETE, siteID, templateID, null, options, callback);
    }

    public void publishTemplate(String siteID, String templateID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/templates/" + templateID + "/publish";
        post(url, options, callback);
    }

    public void refreshTemplate(String siteID, String templateID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/templates/" + templateID + "/refresh";
        post(url, options, callback);
    }

    public void refreshTemplateForSite(String siteID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/refresh_templates";
        post(url, options, callback);
    }

    public void cloneTemplate(String siteID, String templateID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/templates/" + templateID + "/clone";
        post(url, options, callback);
    }

    //MARK: - Templatemap
    public void listTemplatemaps(String siteID, String templateID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/templates/" + templateID + "/templatemaps";
        get(url, options, callback);
    }

    private void templatemapAction(HttpMethod action, String siteID, String templateID, String templateMapID, Resource templateMap, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/templates/" + templateID + "/templatemaps";
        if (action != HttpMethod.POST) {
            if (templateMapID != null && !templateMapID.equals("")) {
                url += "/" + templateMapID;
            }
        }
        action("templatemap", action, url, templateMap, options, callback);
    }

    public void createTemplatemap(String siteID, String templateID, Resource templateMap, Parameter options, ActionCallback callback) {
        templatemapAction(HttpMethod.POST, siteID, templateID, null, templateMap, options, callback);
    }

    public void getTemplatemap(String siteID, String templateID, String templateMapID, Parameter options, ActionCallback callback) {
        templatemapAction(HttpMethod.GET, siteID, templateID, templateMapID, null, options, callback);
    }

    public void updateTemplatemap(String siteID, String templateID, String templateMapID, Resource templateMap, Parameter options, ActionCallback callback) {
        templatemapAction(HttpMethod.PUT, siteID, templateID, templateMapID, templateMap, options, callback);
    }

    public void deleteTemplatemap(String siteID, String templateID, String templateMapID, Parameter options, ActionCallback callback) {
        templatemapAction(HttpMethod.DELETE, siteID, templateID, templateMapID, null, options, callback);
    }

    //MARK: - Widget
    public void listWidgets(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgets";
        get(url, options, callback);
    }

    public void listWidgetsForWidgetset(String siteID, String widgetSetID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgetsets/" + widgetSetID + "/widgets";
        get(url, options, callback);
    }

    public void getWidgetForWidgetset(String siteID, String widgetSetID, String widgetID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgetsets/" + widgetSetID + "/widgets/" + widgetID;
        action("widget", HttpMethod.GET, url, null, options, callback);
    }

    private void widgetAction(HttpMethod action, String siteID, String widgetID, Resource widget, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgets";
        if (action != HttpMethod.POST) {
            if (widgetID != null && !widgetID.equals("")) {
                url += "/" + widgetID;
            }
        }
        action("widget", action, url, widget, options, callback);
    }

    public void createWidget(String siteID, Resource widget, Parameter options, ActionCallback callback) {
        widgetAction(HttpMethod.POST, siteID, null, widget, options, callback);
    }

    public void getWidget(String siteID, String widgetID, Parameter options, ActionCallback callback) {
        widgetAction(HttpMethod.GET, siteID, widgetID, null, options, callback);
    }

    public void updateWidget(String siteID, String widgetID, Resource widget, Parameter options, ActionCallback callback) {
        widgetAction(HttpMethod.PUT, siteID, widgetID, widget, options, callback);
    }

    public void deleteWidget(String siteID, String widgetID, Parameter options, ActionCallback callback) {
        widgetAction(HttpMethod.DELETE, siteID, widgetID, null, options, callback);
    }

    public void refreshWidget(String siteID, String widgetID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgets/" + widgetID + "/refresh";
        post(url, options, callback);
    }

    public void cloneWidget(String siteID, String widgetID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgets/" + widgetID + "/clone";
        post(url, options, callback);
    }

    //MARK: - Widgetset
    public void listWidgetsets(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgetsets";
        get(url, options, callback);
    }

    private void widgetsetAction(HttpMethod action, String siteID, String widgetSetID, Resource widgetSet, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/widgetsets";
        if (action != HttpMethod.POST) {
            if (widgetSetID != null && !widgetSetID.equals("")) {
                url += "/" + widgetSetID;
            }
        }
        action("widgetset", action, url, widgetSet, options,callback);
    }

    public void createWidgetset(String siteID, Resource widgetSet, Parameter options, ActionCallback callback) {
        widgetsetAction(HttpMethod.POST, siteID, null, widgetSet, options, callback);
    }

    public void getWidgetset(String siteID, String widgetSetID, Parameter options, ActionCallback callback) {
        widgetsetAction(HttpMethod.GET, siteID, widgetSetID, null, options, callback);
    }

    public void updateWidgetset(String siteID, String widgetSetID, Resource widgetSet, Parameter options, ActionCallback callback) {
        widgetsetAction(HttpMethod.PUT, siteID, widgetSetID, widgetSet, options, callback);
    }

    public void deleteWidgetset(String siteID, String widgetSetID, Parameter options, ActionCallback callback) {
        widgetsetAction(HttpMethod.DELETE, siteID, widgetSetID, null, options, callback);
    }

    //MARK: - Theme
    public void listThemes(Parameter options, Callback callback) {
        String url = APIURL() + "/themes";
        get(url, options, callback);
    }

    public void getTheme(String themeID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/themes/" + themeID;
        get(url, options, callback);
    }

    public void applyThemeToSite(String siteID, String themeID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/themes/" + themeID + "/apply";
        post(url, options, callback);
    }

    public void uninstallTheme(String themeID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/themes/" + themeID;
        delete(url, options, callback);
    }

    public void exportSiteTheme(String siteID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/export_theme";
        post(url, options, callback);
    }

    //MARK: - Role
    public void listRoles(Parameter options, Callback callback) {
        String url = APIURL() + "/roles";
        get(url, options, callback);
    }

    private void roleAction(HttpMethod action, String roleID, Resource role, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/roles/";
        if (action != HttpMethod.POST) {
            if (roleID != null && !roleID.equals("")) {
                url += "/" + roleID;
            }
        }
        action("role", action, url, role, options, callback);
    }

    public void createRole(Resource role, Parameter options, ActionCallback callback) {
        roleAction(HttpMethod.POST, null, role, options, callback);
    }

    public void getRole(String roleID, Parameter options, ActionCallback callback) {
        roleAction(HttpMethod.GET, roleID, null, options, callback);
    }

    public void updateRole(String roleID, Resource role, Parameter options, ActionCallback callback) {
        roleAction(HttpMethod.PUT, roleID, role, options, callback);
    }

    public void deleteRole(String roleID, Parameter options, ActionCallback callback) {
        roleAction(HttpMethod.DELETE, roleID, null, options, callback);
    }

    //MARK: - Permission
    public void listPermissions(Parameter options, Callback callback) {
        String url = APIURL() + "/permissions";
        get(url, options, callback);
    }

    private void listPermissionsForObject(String objectName, String objectID, Parameter options, Callback callback) {
        String url = APIURL() + "/" + objectName + "/" + objectID + "/permissions";
        get(url, options, callback);
    }

    public void listPermissionsForUser(String userID, Parameter options, Callback callback) {
        listPermissionsForObject("users", userID, options, callback);
    }

    public void listPermissionsForSite(String siteID, Parameter options, Callback callback) {
        listPermissionsForObject("sites", siteID, options, callback);
    }

    public void listPermissionsForRole(String roleID, Parameter options, Callback callback) {
        listPermissionsForObject("roles", roleID, options, callback);
    }

    public void grantPermissionToSite(String siteID, String userID, String roleID, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/permissions/grant";

        Parameter params = new Parameter();
        params.put("user_id", userID);
        params.put("role_id", roleID);

        post(url, params, callback);
    }

    public void grantPermissionToUser(String userID, String siteID, String roleID, ActionCallback callback) {
        String url = APIURL() + "/users/" + userID + "/permissions/grant";

        Parameter params = new Parameter();
        params.put("site_id", siteID);
        params.put("role_id", roleID);

        post(url, params, callback);
    }

    public void revokePermissionFromSite(String siteID, String userID, String roleID, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/permissions/revoke";

        Parameter params = new Parameter();
        params.put("user_id", userID);
        params.put("role_id", roleID);

        post(url, params, callback);
    }

    public void revokePermissionFromUser(String userID, String siteID, String roleID, ActionCallback callback) {
        String url = APIURL() + "/users/" + userID + "/permissions/revoke";

        Parameter params = new Parameter();
        params.put("site_id", siteID);
        params.put("role_id", roleID);

        post(url, params, callback);
    }

    //MARK: - Log
    public void listLogs(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/logs";
        get(url, options, callback);
    }

    private void logAction(HttpMethod action, String siteID, String logID, Resource log, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/logs";
        if (action != HttpMethod.POST) {
            if (logID != null && !logID.equals("")) {
                url += "/" + logID;
            }
        }
        action("log", action, url, log, options, callback);
    }

    public void createLog(String siteID, Resource log, Parameter options, ActionCallback callback) {
        logAction(HttpMethod.POST, siteID, null, log, options, callback);
    }

    public void getLog(String siteID, String logID, Parameter options, ActionCallback callback) {
        logAction(HttpMethod.GET, siteID, logID, null, options, callback);
    }

    public void updateLog(String siteID, String logID, Resource log, Parameter options, ActionCallback callback) {
        logAction(HttpMethod.PUT, siteID, logID, log, options, callback);
    }

    public void deleteLog(String siteID, String logID, Parameter options, ActionCallback callback) {
        logAction(HttpMethod.DELETE, siteID, logID, null, options, callback);
    }

    public void resetLogs(String siteID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/logs";
        delete(url, options, callback);
    }

    public void exportLogs(String siteID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/logs/export";
        get(url, options, callback);
    }

    //MARK: - FormattedText
    public void listFormattedTexts(String siteID, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/formatted_texts";
        get(url, options, callback);
    }

    private void formattedTextAction(HttpMethod action, String siteID, String formattedTextID, Resource formattedText, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/formatted_texts";
        if (action != HttpMethod.POST) {
            if (formattedTextID != null && !formattedTextID.equals("")) {
                url += "/" + formattedTextID;
            }
        }
        action("formatted_text", action, url, formattedText, options, callback);
    }

    public void createFormattedText(String siteID, Resource formattedText, Parameter options, ActionCallback callback) {
        formattedTextAction(HttpMethod.POST, siteID, null, formattedText, options, callback);
    }

    public void getFormattedText(String siteID, String formattedTextID, Parameter options, ActionCallback callback) {
        formattedTextAction(HttpMethod.GET, siteID, formattedTextID, null, options, callback);
    }

    public void updateFormattedText(String siteID, String formattedTextID, Resource formattedText, Parameter options, ActionCallback callback) {
        formattedTextAction(HttpMethod.PUT, siteID, formattedTextID, formattedText, options, callback);
    }

    public void deleteFormattedText(String siteID, String formattedTextID, Parameter options, ActionCallback callback) {
        formattedTextAction(HttpMethod.DELETE, siteID, formattedTextID, null, options, callback);
    }

    //MARK: - Stats
    public void getStatsProvider(String siteID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/sites/" + siteID + "/stats/provider";
        get(url, options, callback);
    }

    private void listStatsForTarget(String siteID, String targetName, String objectName, String startDate, String endDate, Parameter options, Callback callback) {
        String url = APIURL() + "/sites/" + siteID + "/stats/" + targetName + "/" + objectName;

        Parameter params = new Parameter();
        params.put("startDate", startDate);
        params.put("endDate", endDate);

        get(url, params, callback);
    }

    private void listStatsForPath(String siteID, String objectName, String startDate, String endDate, Parameter options, Callback callback) {
        listStatsForTarget(siteID, "path", objectName, startDate, endDate, options, callback);
    }

    public void listStatsPageviewsForPath(String siteID, String startDate, String endDate, Parameter options, Callback callback) {
        listStatsForPath(siteID, "pageviews", startDate, endDate, options, callback);
    }

    public void listStatsVisitsForPath(String siteID, String startDate, String endDate, Parameter options, Callback callback) {
        listStatsForPath(siteID, "visits", startDate, endDate, options, callback);
    }

    private void listStatsForDate(String siteID, String objectName, String startDate, String endDate, Parameter options, Callback callback) {
        listStatsForTarget(siteID, "date", objectName, startDate, endDate, options, callback);
    }

    public void listStatsPageviewsForDate(String siteID, String startDate, String endDate, Parameter options, Callback callback) {
        listStatsForDate(siteID, "pageviews", startDate, endDate, options, callback);
    }

    public void listStatsVisitsForDate(String siteID, String startDate, String endDate, Parameter options, Callback callback) {
        listStatsForDate(siteID, "visits", startDate, endDate, options, callback);
    }

    //MARK: - Plugin
    public void listPlugins(Parameter options, Callback callback) {
        String url = APIURL() + "/plugins";
        get(url, options, callback);
    }

    public void getPlugin(String pluginID, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/plugins/" + pluginID;
        get(url, options, callback);
    }

    private void togglePlugin(String pluginID, boolean enable, Parameter options, ActionCallback callback) {
        String url = APIURL() + "/plugins";
        if (pluginID != null && !pluginID.equals("*")) {
            url += "/" + pluginID;
        }
        url += enable ? "/enable" : "/disable";
        post(url, options, callback);
    }

    public void enablePlugin(String pluginID, Parameter options, ActionCallback callback) {
        togglePlugin(pluginID, true, options, callback);
    }

    public void disablePlugin(String pluginID, Parameter options, ActionCallback callback) {
        togglePlugin(pluginID, false, options, callback);
    }

    public void enableAllPlugins(Parameter options, ActionCallback callback) {
        togglePlugin("*", true, options, callback);
    }

    public void disableAllPlugins(Parameter options, ActionCallback callback) {
        togglePlugin("*", false, options, callback);
    }

    //MARK: - # V3
    //MARK: - Version
    public void version(Parameter options, final ActionCallback callback) {
        String url = APIBaseURL + "/version";

        get(url, options, new ActionCallback() {
            public void onSuccess(JSONObject response) {
                if (response != null) {
                    if (response.has("endpointVersion")) {
                        endpointVersion = response.getString("endpointVersion");
                    }
                    if (response.has("apiVersion")) {
                        apiVersion = response.getString("apiVersion");
                    }
                }
                callback.onSuccess(response);
            }

            public void onFailure(JSONObject error) {
                callback.onFailure(error);
            }
        });
    }
}
