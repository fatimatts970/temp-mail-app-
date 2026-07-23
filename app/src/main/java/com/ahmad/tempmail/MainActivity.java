package com.ahmad.tempmail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
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
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentEmail, tvInboxContent, tvInboxStatus;
    private Button btnDelete, btnRandom, btnChoose, btnCopy;
    private RequestQueue requestQueue;
    private String currentUsername = "";
    private String currentDomain = "";
    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;
    private List<String> domainList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCurrentEmail = findViewById(R.id.tvCurrentEmail);
        tvInboxContent = findViewById(R.id.tvInboxContent);
        tvInboxStatus = findViewById(R.id.tvInboxStatus);

        btnDelete = findViewById(R.id.btnDelete);
        btnRandom = findViewById(R.id.btnRandom);
        btnChoose = findViewById(R.id.btnChoose);
        btnCopy = findViewById(R.id.btnCopy);

        requestQueue = Volley.newRequestQueue(this);

        fetchDomains();
        generateRandomEmail();

        btnRandom.setOnClickListener(v -> generateRandomEmail());

        btnDelete.setOnClickListener(v -> {
            tvCurrentEmail.setText("No active email");
            tvInboxContent.setText("Email cleared. Click RANDOM to create new.");
            currentUsername = "";
            currentDomain = "";
        });

        btnCopy.setOnClickListener(v -> {
            String fullEmail = tvCurrentEmail.getText().toString();
            if (!fullEmail.isEmpty() && !fullEmail.equals("generating...") && !fullEmail.equals("No active email")) {
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
                autoRefreshHandler.postDelayed(this, 10000);
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 5000);
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
                        checkInbox();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> Toast.makeText(MainActivity.this, "Error generating email", Toast.LENGTH_SHORT).show());
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
                }, error -> {});
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
                Toast.makeText(MainActivity.this, "Enter a valid name", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedDomain = spinnerDomains.getSelectedItem().toString();
            currentUsername = name;
            currentDomain = selectedDomain;
            tvCurrentEmail.setText(currentUsername + "@" + currentDomain);
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
                        tvInboxContent.setText("No messages received yet...");
                    } else {
                        tvInboxStatus.setText("Inbox Messages (" + response.length() + ")");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                JSONObject obj = response.getJSONObject(i);
                                String from = obj.getString("from");
                                String subject = obj.getString("subject");
                                String date = obj.getString("date");

                                sb.append("📩 From: ").append(from).append("\n")
                                  .append("📌 Subject: ").append(subject).append("\n")
                                  .append("🕒 Date: ").append(date).append("\n\n")
                                  .append("-----------------------------------\n\n");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        tvInboxContent.setText(sb.toString());
                    }
                }, error -> {});
        requestQueue.add(request);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }
}
