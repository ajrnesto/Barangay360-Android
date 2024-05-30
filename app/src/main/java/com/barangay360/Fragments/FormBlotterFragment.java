package com.barangay360.Fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.barangay360.Adapters.ImagePreviewAdapter;
import com.barangay360.Adapters.InvolvedPersonAdapter;
import com.barangay360.Objects.InvolvedPerson;
import com.barangay360.R;
import com.barangay360.SelectLocationActivity;
import com.barangay360.Utils.Utils;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class FormBlotterFragment extends Fragment implements ImagePreviewAdapter.OnImagePreviewListener, InvolvedPersonAdapter.OnInvolvedPersonListener {

    FirebaseFirestore DB;
    FirebaseStorage STORAGE;
    FirebaseAuth AUTH;
    FirebaseUser USER;

    private void initializeFirebase() {
        DB = FirebaseFirestore.getInstance();
        STORAGE = FirebaseStorage.getInstance();
        AUTH = FirebaseAuth.getInstance();
        USER = AUTH.getCurrentUser();
    }

    ArrayList<Uri> arrUri = new ArrayList<>();
    ArrayList<InvolvedPerson> arrInvolvedPerson = new ArrayList<>();
    ArrayList<String> arrInvolvedPersonSearchKeys = new ArrayList<>();
    ImagePreviewAdapter imagePreviewAdapter;
    ImagePreviewAdapter.OnImagePreviewListener onImagePreviewListener = this;
    InvolvedPersonAdapter involvedPersonAdapter;
    InvolvedPersonAdapter.OnInvolvedPersonListener onInvolvedPersonListener = this;

    ActivityResultLauncher<Intent> activityResultLauncher;
    int SELECT_IMAGE_CODE = 1;

    View view;
    ConstraintLayout clProgress;

    CircularProgressIndicator progressUploading;
    TextView tvUploading;
    TextInputEditText etIncidentDate, etLocationPurok, etIncidentDetails, etLocation;
    AutoCompleteTextView menuIncidentType;
    RecyclerView rvMedia, rvInvolvedPersons;
    MaterialButton btnAddInvolvedPerson, btnAddMedia, btnSubmit;

    // date picker items
    MaterialDatePicker.Builder<Long> IncidentDate;
    MaterialDatePicker<Long> dpIncidentDate;
    long dpIncidentDateSelection = 0;

    // Spinner items
    String[] itemsIncidentType;
    ArrayAdapter<String> adapterIncidentType;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_form_blotter, container, false);

        initializeFirebase();
        initializeViews();
        initializeActivityResultLauncher();
        initializeSpinners();
        initializeDatePicker();
        handleUserInteraction();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Utils.Cache.setBoolean(requireContext(), "appointment_items_selection_mode", false);

        double latitude = Utils.Cache.getDouble(requireContext(), "selected_latitude");
        double longitude = Utils.Cache.getDouble(requireContext(), "selected_longitude");
        String addressLine = Utils.Cache.getString(requireContext(), "addressLine");

        if (latitude == 0 || longitude == 0) {
            Objects.requireNonNull(etLocation.getText()).clear();
            return;
        }

        etLocation.setText(addressLine);
    }



    @Override
    public void onDestroy() {
        super.onDestroy();

        Utils.Cache.removeKey(requireContext(), "selected_latitude");
        Utils.Cache.removeKey(requireContext(), "selected_longitude");
        Utils.Cache.removeKey(requireContext(), "addressLine");
    }

    private void initializeViews() {
        clProgress = view.findViewById(R.id.clProgress);
        progressUploading = view.findViewById(R.id.progressUploading);
        tvUploading = view.findViewById(R.id.tvUploading);
        etIncidentDate = view.findViewById(R.id.etIncidentDate);
        etLocationPurok = view.findViewById(R.id.etLocationPurok);
        etLocation = view.findViewById(R.id.etLocation);
        etIncidentDetails = view.findViewById(R.id.etIncidentDetails);
        menuIncidentType = view.findViewById(R.id.menuIncidentType);
        rvMedia = view.findViewById(R.id.rvMedia);
        rvInvolvedPersons = view.findViewById(R.id.rvInvolvedPersons);
        btnAddInvolvedPerson = view.findViewById(R.id.btnAddInvolvedPerson);
        btnAddMedia = view.findViewById(R.id.btnAddMedia);
        btnSubmit = view.findViewById(R.id.btnSubmit);

        rvMedia.setHasFixedSize(false);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), 3);
        rvMedia.setLayoutManager(gridLayoutManager);
        imagePreviewAdapter = new ImagePreviewAdapter(requireContext(), arrUri, onImagePreviewListener);
        rvMedia.setAdapter(imagePreviewAdapter);

        rvInvolvedPersons.setHasFixedSize(false);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(requireContext());
        rvInvolvedPersons.setLayoutManager(linearLayoutManager);
        involvedPersonAdapter = new InvolvedPersonAdapter(requireContext(), arrInvolvedPerson, onInvolvedPersonListener);
        rvInvolvedPersons.setAdapter(involvedPersonAdapter);

        // prefill complainant
        DB.collection("users").document(AUTH.getUid()).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    String firstName = task.getResult().getString("firstName");
                    String middleName = task.getResult().getString("middleName");
                    String lastName = task.getResult().getString("lastName");
                    String addressPurok = task.getResult().getString("addressPurok");

                    arrInvolvedPerson.add(new InvolvedPerson(firstName + " " + middleName.charAt(0) + ". " + lastName, addressPurok + ", CABANGAHAN, SIATON", "COMPLAINANT"));
                    involvedPersonAdapter.notifyItemInserted(0);
                }
            }
        });
    }

    private void initializeSpinners() {
        itemsIncidentType = new String[]{"Burglary","Child Abuse","Disturbance","Domestic Disputes","Drug-related","Fraud","Harassment","Illegal Gambling","Juvenile Delinquency","Missing Person","Noise Complaints","Petty Quarrels","Physical Altercation","Property Damage","Public Indecency","Public Intoxication","Public Nuisance","Theft","Traffic Violations","Trespassing","Other"};
        adapterIncidentType = new ArrayAdapter<>(requireContext(), R.layout.list_item, itemsIncidentType);
        menuIncidentType.setAdapter(adapterIncidentType);
    }

    private void initializeDatePicker() {
        IncidentDate = MaterialDatePicker.Builder.datePicker();
        IncidentDate.setTitleText("Select Incident Date")
                .setSelection(System.currentTimeMillis());
        dpIncidentDate = IncidentDate.build();
        dpIncidentDate.addOnPositiveButtonClickListener(selection -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy");
            dpIncidentDateSelection = dpIncidentDate.getSelection();
            etIncidentDate.setText(sdf.format(dpIncidentDateSelection).toUpperCase(Locale.ROOT));
            etIncidentDate.setEnabled(true);
        });
        dpIncidentDate.addOnNegativeButtonClickListener(view -> {
            etIncidentDate.setEnabled(true);
        });
        dpIncidentDate.addOnCancelListener(dialogInterface -> {
            etIncidentDate.setEnabled(true);
        });
    }

    private void handleUserInteraction() {
        btnAddInvolvedPerson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                arrInvolvedPerson.add(new InvolvedPerson("", "", ""));
                involvedPersonAdapter.notifyItemInserted(arrInvolvedPerson.size()-1);
            }
        });

        etLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(requireActivity(), SelectLocationActivity.class));
            }
        });

        btnAddMedia.setOnClickListener(view -> selectImageFromDevice());

        btnSubmit.setOnClickListener(view -> validateIncidentReportForm());

        etIncidentDate.setOnClickListener(view -> {
            etIncidentDate.setEnabled(false);
            dpIncidentDate.show(requireActivity().getSupportFragmentManager(), "INCIDENT_DATE_PICKER");
        });
    }

    private void validateIncidentReportForm() {
        btnSubmit.setEnabled(false);
        if (menuIncidentType.getText().toString().isEmpty() ||
                etIncidentDate.getText().toString().isEmpty() ||
                etLocationPurok.getText().toString().isEmpty() ||
                etIncidentDetails.getText().toString().isEmpty())
        {
            Toast.makeText(requireContext(), "Please fill out all the text fields", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            return;
        }

        // validate involved persons
        if (arrInvolvedPerson.size() == 0) {
            Toast.makeText(requireContext(), "Please add at least 1 person involved", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            return;
        }
        int emptyInvolvedPersonFields = 0;
        for (int i = 0; i < rvInvolvedPersons.getAdapter().getItemCount(); i++) {
            View itemView = rvInvolvedPersons.getChildAt(i);
            TextInputEditText etFullName = itemView.findViewById(R.id.etFullName);
            TextInputEditText etFullAddress = itemView.findViewById(R.id.etFullAddress);

            String fullName = etFullName.getText().toString().toUpperCase();
            String fullAddress = etFullAddress.getText().toString().toUpperCase();

            if (fullName.isEmpty() || fullAddress.isEmpty()) {
                Toast.makeText(requireContext(), "Please complete all involved persons' information", Toast.LENGTH_SHORT).show();
                btnSubmit.setEnabled(true);
                return;
            }
            arrInvolvedPerson.get(i).setFullName(fullName);
            arrInvolvedPerson.get(i).setFullAddress(fullAddress);

            Log.d("TAG", "etFullName: "+etFullName.getText().toString().toUpperCase()+"\n etFullAddress: "+etFullAddress.getText().toString().toUpperCase());
        }

        String crimeType = menuIncidentType.getText().toString().toUpperCase();
        long crimeDate = dpIncidentDateSelection;
        String locationPurok = etLocationPurok.getText().toString().toUpperCase();
        String crimeDetails = etIncidentDetails.getText().toString().toUpperCase();

        // store media file names
        ArrayList<String> arrMediaFileNames = new ArrayList<>();
        for (Uri uri : arrUri) {
            arrMediaFileNames.add(uri.getLastPathSegment()+System.currentTimeMillis());
        }

        arrInvolvedPersonSearchKeys.clear();
        for (InvolvedPerson involvedPerson : arrInvolvedPerson) {
            String fullName = involvedPerson.getFullName();
            arrInvolvedPersonSearchKeys.addAll(Arrays.asList(fullName.split(" ")));
        }

        if (arrMediaFileNames.size() == 0) {
            Map<String, Object> blotter = new HashMap<>();
            blotter.put("userUid", AUTH.getUid());
            blotter.put("incidentType", crimeType);
            blotter.put("incidentDate", crimeDate);
            blotter.put("locationPurok", locationPurok);
            blotter.put("incidentDetails", crimeDetails);
            blotter.put("involvedPersons", arrInvolvedPerson);
            blotter.put("involvedPersonsSearchKeys", arrInvolvedPersonSearchKeys);
            blotter.put("mediaFileNames", arrMediaFileNames);
            blotter.put("timestamp", System.currentTimeMillis());
            blotter.put("status", "PENDING");
            HashMap<String, Object> locLatLng = new HashMap<>();
            locLatLng.put("latitude", Utils.Cache.getDouble(requireContext(), "selected_latitude"));
            locLatLng.put("longitude", Utils.Cache.getDouble(requireContext(), "selected_longitude"));
            locLatLng.put("addressLine", Utils.Cache.getString(requireContext(), "addressLine"));
            blotter.put("locationLatLng", locLatLng);

            DocumentReference refIncident =  DB.collection("blotter").document();
            blotter.put("uid", refIncident.getId());

            refIncident.set(blotter)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            requireActivity().onBackPressed();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            btnSubmit.setEnabled(true);
                        }
                    });
        }
        else {
            Map<String, Object> blotter = new HashMap<>();
            blotter.put("userUid", AUTH.getUid());
            blotter.put("incidentType", crimeType);
            blotter.put("incidentDate", crimeDate);
            blotter.put("locationPurok", locationPurok);
            blotter.put("incidentDetails", crimeDetails);
            blotter.put("involvedPersons", arrInvolvedPerson);
            blotter.put("involvedPersonsSearchKeys", arrInvolvedPersonSearchKeys);
            blotter.put("mediaFileNames", arrMediaFileNames);
            blotter.put("timestamp", System.currentTimeMillis());
            blotter.put("status", "PENDING");
            HashMap<String, Object> locLatLng = new HashMap<>();
            locLatLng.put("latitude", Utils.Cache.getDouble(requireContext(), "selected_latitude"));
            locLatLng.put("longitude", Utils.Cache.getDouble(requireContext(), "selected_longitude"));
            locLatLng.put("addressLine", Utils.Cache.getString(requireContext(), "addressLine"));
            blotter.put("locationLatLng", locLatLng);

            // upload all media to firebase storage
            final int[] filesUploaded = {0};
            for (int i = 0; i < arrUri.size(); i++) {
                Uri uri = arrUri.get(i);
                clProgress.setVisibility(View.VISIBLE);
                tvUploading.setText("Uploading media ("+ filesUploaded[0] +"/"+arrUri.size()+")");

                StorageReference refMedia = STORAGE.getReference().child("media/"+arrMediaFileNames.get(i));

                UploadTask uploadTask = refMedia.putFile(uri);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(requireContext(), "Upload failed: "+e, Toast.LENGTH_SHORT).show();

                        clProgress.setVisibility(View.GONE);
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        filesUploaded[0]++;
                        tvUploading.setText("Uploading media ("+ filesUploaded[0] +"/"+arrUri.size()+")");

                        if (filesUploaded[0] == arrUri.size()) {
                            clProgress.setVisibility(View.GONE);

                            DocumentReference refIncident =  DB.collection("blotter").document();
                            blotter.put("uid", refIncident.getId());

                            refIncident.set(blotter)
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            requireActivity().onBackPressed();
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            btnSubmit.setEnabled(true);
                                        }
                                    });
                        }
                    }
                });
            }
        }
    }

    private void selectImageFromDevice() {
        Intent iImageSelect = new Intent();
        iImageSelect.setType("image/* video/*");
        iImageSelect.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        iImageSelect.setAction(Intent.ACTION_PICK);

        activityResultLauncher.launch(Intent.createChooser(iImageSelect, "Upload supporting documents"));
    }

    private void initializeActivityResultLauncher() {
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        int count = result.getData().getClipData().getItemCount();

                        for (int i = 0; i < count; i++) {
                            Uri uriMedia = result.getData().getClipData().getItemAt(i).getUri();
                            arrUri.add(uriMedia);

                            imagePreviewAdapter.notifyDataSetChanged();
                        }
                    }
                }
        );
    }

    @Override
    public void onImagePreviewClick(int position) {
        arrUri.remove(position);
        imagePreviewAdapter.notifyItemRemoved(position);
    }

    @Override
    public void onInvolvedPersonListener(int position) {
        if (arrInvolvedPerson.size() > 0) {
            View itemView = rvInvolvedPersons.getChildAt(position);

            TextInputEditText etFullName = itemView.findViewById(R.id.etFullName);
            TextInputEditText etFullAddress = itemView.findViewById(R.id.etFullAddress);
            etFullName.getText().clear();
            etFullAddress.getText().clear();

            arrInvolvedPerson.remove(position);
            involvedPersonAdapter.notifyItemRemoved(position);
        }
    }
}