package com.ahmad.tempmail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentEmail, tvInboxContent, tvInboxStatus;
    private Button btnDelete, btnRandom, btnChoose, btnCopy;
    private Spinner spinnerHistory;
    private RequestQueue requestQueue;
    private String currentUsername = "";
    private String currentDomain = "";
    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;
    private List<String> domainList = new ArrayList<>();
    private List<String> historyList = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("TempMailPrefs", MODE_PRIVATE);

        tvCurrentEmail = findViewById(R.id.tvCurrentEmail);
        tvInboxContent = findViewById(R.id.tvInboxContent);
        tvInboxStatus = findViewById(R.id.tvInboxStatus);
        spinnerHistory = findViewById(R.id.spinnerHistory);

        btnDelete = findViewById(R.id.btnDelete);
        btnRandom = findViewById(R.id.btnRandom);
        btnChoose = findViewById(R.id.btnChoose);
        btnCopy = findViewById(R.id.btnCopy);

        requestQueue = Volley.newRequestQueue(this);

        setupHistory();
        fetchDomains();
        generateRandomEmail();

        btnRandom.setOnClickListener(v -> generateRandomEmail());

        btnDelete.setOnClickListener(v -> generateRandomEmail());

        btnCopy.setOnClickListener(v -> {
            String fullEmail = tvCurrentEmail.getText().toString();
            if (!fullEmail.isEmpty() && !fullEmail.contains("generating")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("TempEmail", fullEmail);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        btnChoose.setOnClickListener(v -> showChooseDialog());

        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                checkInbox();
                autoRefreshHandler.postDelayed(this, 5000);
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 3000);
    }

    private void setupHistory() {
        Set<String> savedSet = prefs.getStringSet("email_history", new HashSet<>());
        historyList.clear();
        historyList.addAll(savedSet);

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, historyList);
        spinnerHistory.setAdapter(historyAdapter);

        spinnerHistory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < historyList.size()) {
                    String selectedEmail = historyList.get(position);
                    if (selectedEmail.contains("@")) {
                        String[] parts = selectedEmail.split("@");
                        currentUsername = parts[0];
                        currentDomain = parts[1];
                        tvCurrentEmail.setText(selectedEmail);
                        checkInbox();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveToHistory(String email) {
        if (!historyList.contains(email)) {
            historyList.add(0, email);
            Set<String> set = new HashSet<>(historyList);
            prefs.edit().putStringSet("email_history", set).apply();
            historyAdapter.notifyDataSetChanged();
            spinnerHistory.setSelection(0);
        }
    }

    private void generateRandomEmail() {
        String url = "https://www.1secmail.com/api/v1/?action=genRandomMailbox&count=1";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        String fullEmail = response.getString(0);
                        tvCurrentEmail.setText(fullEmail);
                        String[] parts = fullEmail.split("@");
                        currentUsername = parts[0];
                        currentDomain = parts[1];
                        saveToHistory(fullEmail);
                        checkInbox();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> Toast.makeText(MainActivity.this, "Network Error, Retrying...", Toast.LENGTH_SHORT).show()) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                return headers;
            }
        };
        requestQueue.add(request);
    }

    private void fetchDomains() {
        String url = "https://www.1secmail.com/api/v1/?action=getDomainList";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    domainList.clear();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            domainList.add(response.getString(i));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, error -> {}) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                return headers;
            }
        };
        requestQueue.add(request);
    }

    private void showChooseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_choose, null);
        builder.setView(view);

        EditText etCustomName = view.findViewById(R.id.etCustomName);
        Spinner spinnerDomains = view.findViewById(R.id.spinnerDomains);
        Button btnDialogCancel = view.findViewById(R.id.btnDialogCancel);
        Button btnDialogCreate = view.findViewById(R.id.btnDialogCreate);

        if (domainList.isEmpty()) {
            domainList.add("1secmail.com");
            domainList.add("1secmail.org");
            domainList.add("1secmail.net");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, domainList);
        spinnerDomains.setAdapter(adapter);

        AlertDialog dialog = builder.create();
        btnDialogCancel.setOnClickListener(v -> dialog.dismiss());

        btnDialogCreate.setOnClickListener(v -> {
            String name = etCustomName.getText().toString().trim().toLowerCase();
            if (name.isEmpty()) {
                Toast.makeText(MainActivity.this, "Enter valid name", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedDomain = spinnerDomains.getSelectedItem().toString();
            currentUsername = name;
            currentDomain = selectedDomain;
            String fullEmail = currentUsername + "@" + currentDomain;
            tvCurrentEmail.setText(fullEmail);
            saveToHistory(fullEmail);
            checkInbox();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void checkInbox() {
        if (currentUsername.isEmpty() || currentDomain.isEmpty()) return;

        String url = "https://www.1secmail.com/api/v1/?action=getMessages&login=" + currentUsername + "&domain=" + currentDomain;
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    if (response.length() == 0) {
                        tvInboxStatus.setText("Inbox Messages (0)");
                        tvInboxContent.setText("Waiting for incoming emails...\n(Auto refreshing every 5s)");
                    } else {
                        tvInboxStatus.setText("Inbox Messages (" + response.length() + ")");
                        tvInboxContent.setText("");
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                JSONObject obj = response.getJSONObject(i);
                                int msgId = obj.getInt("id");
                                fetchFullMessage(msgId);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }, error -> {}) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                return headers;
            }
        };
        requestQueue.add(request);
    }

    private void fetchFullMessage(int msgId) {
        String url = "https://www.1secmail.com/api/v1/?action=readMessage&login=" + currentUsername + "&domain=" + currentDomain + "&id=" + msgId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        String from = response.getString("from");
                        String subject = response.getString("subject");
                        String date = response.getString("date");
                        String textBody = response.optString("textBody", "No text content");

                        String formattedMsg = "📩 FROM: " + from + "\n" +
                                "📌 SUBJECT: " + subject + "\n" +
                                "🕒 DATE: " + date + "\n\n" +
                                "💬 MESSAGE / OTP:\n" + textBody + "\n\n" +
                                "===================================\n\n";

                        tvInboxContent.append(formattedMsg);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> {}) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                return headers;
            }
        };
        requestQueue.add(request);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }
}
