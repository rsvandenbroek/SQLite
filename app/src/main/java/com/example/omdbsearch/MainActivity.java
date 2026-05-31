package com.example.omdbsearch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String OMDB_BASE_URL = "https://www.omdbapi.com/";
    private static final int MAX_RESULTS = 10;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText searchInput;
    private Spinner typeSpinner;
    private Button searchButton;
    private ProgressBar progressBar;
    private LinearLayout resultsContainer;
    private TextView statusText;
    private String selectedType = "movie";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("OMDb Zoeken");
        setContentView(createContentView());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Films en series zoeken");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title, fullWidthWrapHeight());

        searchInput = new EditText(this);
        searchInput.setHint("Bijvoorbeeld: Batman, Friends, Inception");
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchOmdb();
                return true;
            }
            return false;
        });
        root.addView(searchInput, fullWidthWrapHeight());

        typeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Films", "Series"}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedType = position == 0 ? "movie" : "series";
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedType = "movie";
            }
        });
        root.addView(typeSpinner, fullWidthWrapHeightWithTopMargin(12));

        searchButton = new Button(this);
        searchButton.setText("Zoeken");
        searchButton.setOnClickListener(view -> searchOmdb());
        root.addView(searchButton, fullWidthWrapHeightWithTopMargin(12));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, centeredWrapContentWithTopMargin(16));

        statusText = new TextView(this);
        statusText.setText("Vul een trefwoord in om informatie uit OMDb op te zoeken.");
        statusText.setTextSize(16);
        statusText.setPadding(0, dp(18), 0, dp(10));
        root.addView(statusText, fullWidthWrapHeight());

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsContainer, fullWidthWrapHeight());

        return scrollView;
    }

    private void searchOmdb() {
        String keyword = searchInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "Vul eerst een trefwoord in.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true, "Zoeken naar \"" + keyword + "\"...");
        resultsContainer.removeAllViews();

        executorService.execute(() -> {
            try {
                String query = "s=" + encode(keyword) + "&type=" + selectedType + "&page=1";
                JSONObject response = requestJson(query);
                if (!"True".equalsIgnoreCase(response.optString("Response"))) {
                    postError(response.optString("Error", "Geen resultaten gevonden."));
                    return;
                }

                JSONArray searchResults = response.getJSONArray("Search");
                List<MovieResult> movies = new ArrayList<>();
                for (int i = 0; i < searchResults.length() && i < MAX_RESULTS; i++) {
                    movies.add(MovieResult.fromJson(searchResults.getJSONObject(i)));
                }
                mainHandler.post(() -> showSearchResults(movies));
            } catch (Exception exception) {
                postError("Zoeken mislukt: " + exception.getMessage());
            }
        });
    }

    private void showSearchResults(List<MovieResult> movies) {
        setLoading(false, movies.size() + " resultaat/resultaten gevonden. Tik op een titel voor details.");
        resultsContainer.removeAllViews();

        for (MovieResult movie : movies) {
            TextView resultView = new TextView(this);
            resultView.setText(movie.title + " (" + movie.year + ")\nType: " + movie.type + "\nIMDb ID: " + movie.imdbId);
            resultView.setTextSize(17);
            resultView.setPadding(dp(14), dp(12), dp(14), dp(12));
            resultView.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
            resultView.setOnClickListener(view -> loadDetails(movie.imdbId));
            resultsContainer.addView(resultView, fullWidthWrapHeightWithTopMargin(10));
        }
    }

    private void loadDetails(String imdbId) {
        setLoading(true, "Details laden...");

        executorService.execute(() -> {
            try {
                JSONObject details = requestJson("i=" + encode(imdbId) + "&plot=full");
                if (!"True".equalsIgnoreCase(details.optString("Response"))) {
                    postError(details.optString("Error", "Details konden niet worden geladen."));
                    return;
                }
                mainHandler.post(() -> showDetails(details));
            } catch (Exception exception) {
                postError("Details laden mislukt: " + exception.getMessage());
            }
        });
    }

    private void showDetails(JSONObject details) {
        setLoading(false, "Details geladen.");
        resultsContainer.removeAllViews();

        TextView detailView = new TextView(this);
        detailView.setText(buildDetailsText(details));
        detailView.setTextSize(16);
        detailView.setPadding(dp(14), dp(12), dp(14), dp(12));
        detailView.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        resultsContainer.addView(detailView, fullWidthWrapHeightWithTopMargin(10));

        Button backButton = new Button(this);
        backButton.setText("Nieuwe zoekopdracht");
        backButton.setOnClickListener(view -> {
            resultsContainer.removeAllViews();
            statusText.setText("Vul een trefwoord in om opnieuw te zoeken.");
            searchInput.requestFocus();
        });
        resultsContainer.addView(backButton, fullWidthWrapHeightWithTopMargin(12));
    }

    private String buildDetailsText(JSONObject details) {
        return value(details, "Title") + " (" + value(details, "Year") + ")\n\n"
                + "Type: " + value(details, "Type") + "\n"
                + "Genre: " + value(details, "Genre") + "\n"
                + "Regisseur: " + value(details, "Director") + "\n"
                + "Acteurs: " + value(details, "Actors") + "\n"
                + "IMDb-score: " + value(details, "imdbRating") + "\n"
                + "Released: " + value(details, "Released") + "\n"
                + "Speelduur: " + value(details, "Runtime") + "\n\n"
                + "Plot:\n" + value(details, "Plot");
    }

    private JSONObject requestJson(String query) throws IOException, JSONException {
        URL url = new URL(OMDB_BASE_URL + "?apikey=" + encode(BuildConfig.OMDB_API_KEY) + "&" + query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        String body = readFully(stream);
        connection.disconnect();

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + ": " + body);
        }
        return new JSONObject(body);
    }

    private String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private void setLoading(boolean loading, String message) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!loading);
        statusText.setText(message);
    }

    private void postError(String message) {
        mainHandler.post(() -> {
            setLoading(false, message);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private String value(JSONObject jsonObject, String key) {
        String value = jsonObject.optString(key, "Onbekend");
        return value.isEmpty() || "N/A".equalsIgnoreCase(value) ? "Onbekend" : value;
    }

    private String encode(String value) {
        return Uri.encode(value);
    }

    private LinearLayout.LayoutParams fullWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams fullWidthWrapHeightWithTopMargin(int topMarginDp) {
        LinearLayout.LayoutParams params = fullWidthWrapHeight();
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams centeredWrapContentWithTopMargin(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class MovieResult {
        private final String title;
        private final String year;
        private final String imdbId;
        private final String type;

        private MovieResult(String title, String year, String imdbId, String type) {
            this.title = title;
            this.year = year;
            this.imdbId = imdbId;
            this.type = type;
        }

        private static MovieResult fromJson(JSONObject jsonObject) {
            return new MovieResult(
                    jsonObject.optString("Title", "Onbekende titel"),
                    jsonObject.optString("Year", "Onbekend"),
                    jsonObject.optString("imdbID", ""),
                    jsonObject.optString("Type", "Onbekend")
            );
        }
    }
}
