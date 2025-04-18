package com.example.collabasket.ui.fragments;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.Manifest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Contact;
import com.example.collabasket.model.ProduitGroupes;
import com.example.collabasket.ui.adapter.ContactAdapter;
import com.example.collabasket.ui.adapter.ProduitGroupesAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ListeGroupesFragment extends Fragment {

    private String groupId, groupName;
    private FirebaseFirestore firestore;
    private ProduitGroupesAdapter produitAdapter;
    private RecyclerView recyclerView;
    private List<Contact> contactsList = new ArrayList<>();
    private ActivityResultLauncher<String> contactsPermissionLauncher;
    private ActivityResultLauncher<String> smsPermissionLauncher;
    private Contact contactEnCoursPourSms;
    private TextView textEmptyList;
    private final String[] unitesDisponibles = { "pcs", "g", "kg", "ml", "L" };
    private final String[] categoriesDisponibles = {
            "Fruits et légumes", "Viandes et poissons", "Produits laitiers", "Boulangerie",
            "Épicerie sucrée", "Épicerie salée", "Boissons", "Surgelés",
            "Produits ménagers", "Hygiène et beauté", "Bébé", "Animaux",
            "Papeterie", "Textile", "Électronique", "Autre"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_liste_groupes, container, false);

        ImageButton btnRetour = rootView.findViewById(R.id.btn_retour_groupes);
        btnRetour.setOnClickListener(v -> revenirAGroupes());

        if (getArguments() != null) {
            groupId = getArguments().getString("groupId", "");
            groupName = getArguments().getString("groupName", "");
        }

        setupPermissions();
        setupToolbar(rootView);
        setupRecyclerView(rootView);
        setupFloatingActionButton(rootView);

        recupererRoleEtInitialiserInterface(rootView);

        return rootView;
    }

    private void revenirAGroupes() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new GroupesFragment())
                .commit();
    }

    private void setupPermissions() {
        contactsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        loadContactsEtAfficherDialogue();
                    } else {
                        Toast.makeText(getContext(), "Permission refusée : impossible de lire les contacts", Toast.LENGTH_LONG).show();
                    }
                }
        );
        smsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted && contactEnCoursPourSms != null) {
                        envoyerInvitationParSms(contactEnCoursPourSms);
                        contactEnCoursPourSms = null;
                    } else {
                        Toast.makeText(getContext(), "Permission SMS refusée", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupToolbar(View rootView) {
        TextView titre = rootView.findViewById(R.id.text_group_title);
        textEmptyList = rootView.findViewById(R.id.text_empty_list);
        titre.setText(groupName);
    }

    private void setupRecyclerView(View rootView) {
        firestore = FirebaseFirestore.getInstance();
        recyclerView = rootView.findViewById(R.id.recycler_produits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupFloatingActionButton(View rootView) {
        FloatingActionButton fab = rootView.findViewById(R.id.fab_ajouter_groupe);
        fab.setOnClickListener(v -> showAddProduitDialog());
    }

    private void recupererRoleEtInitialiserInterface(View rootView) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        firestore.collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("members." + currentUserId + ".role");
                    if (role == null) role = "Membre";

                    produitAdapter = new ProduitGroupesAdapter(groupId, role, requireContext());
                    recyclerView.setAdapter(produitAdapter);

                    chargerProduitsEnTempsReel();
                    setupMenuOptions(rootView, role);
                });
    }

    private void setupMenuOptions(View rootView, String role) {
        ImageButton menuButton = rootView.findViewById(R.id.button_menu_options);
        menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.menu_groupe, popup.getMenu());

            if ("Membre".equals(role)) {
                popup.getMenu().findItem(R.id.menu_ajouter_membre).setVisible(false);
            }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.menu_ajouter_membre) {
                    checkContactsPermissionEtCharger();
                    return true;
                } else if (id == R.id.menu_info_groupe) {
                    InfoGroupeFragment infoGroupeFragment = new InfoGroupeFragment();
                    Bundle args = new Bundle();
                    if (groupId != null && !groupId.isEmpty()) {
                        args.putString("groupId", groupId);
                    }
                    infoGroupeFragment.setArguments(args);
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, infoGroupeFragment)
                            .addToBackStack(null)
                            .commit();
                    return true;
                } else if (id == R.id.action_historique_groupe) {
                    HistoriqueGroupeFragment historiqueFragment = new HistoriqueGroupeFragment();
                    Bundle args = new Bundle();
                    args.putString("groupId", groupId);
                    historiqueFragment.setArguments(args);

                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, historiqueFragment)
                            .addToBackStack(null)
                            .commit();
                    return true;
                } else if (id == R.id.menu_quitter_groupe) {
                    quitterGroupe();
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }
    private void checkContactsPermissionEtCharger() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadContactsEtAfficherDialogue();
        } else {
            contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS);
        }
    }

    private void loadContactsEtAfficherDialogue() {
        contactsList.clear();
        loadContacts();
    }

    private void loadContacts() {
        ContentResolver resolver = requireContext().getContentResolver();
        Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );

        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                HashSet<String> numerosVus = new HashSet<>();

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String phone = cursor.getString(phoneIndex).replaceAll("[\\s\\-()]", "");

                    String cle = name + "-" + phone;
                    if (!numerosVus.contains(cle)) {
                        numerosVus.add(cle);
                        contactsList.add(new Contact(name, phone));
                        Log.d("CONTACTS_DEBUG", "Contact lu : " + name + " - " + phone);
                    }
                }
            } finally {
                cursor.close();
            }
            checkExistingUsers();
        } else {
            Log.w("CONTACTS_DEBUG", "Le curseur est nul. Aucun contact lu.");
        }
    }

    private void checkExistingUsers() {
        final int totalContacts = contactsList.size();
        final int[] processed = {0};

        if (totalContacts == 0) {
            showContactInviteDialog();
            return;
        }

        for (Contact contact : contactsList) {
            String rawPhone = normaliserNumero(contact.getPhone());
            contact.setPhone(rawPhone);

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("phone", rawPhone)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            contact.setHasApp(true);
                        }

                        processed[0]++;
                        if (processed[0] == totalContacts) {
                            showContactInviteDialog();
                        }
                    })
                    .addOnFailureListener(e -> {
                        processed[0]++;
                        if (processed[0] == totalContacts) {
                            showContactInviteDialog();
                        }
                    });
        }
    }

    private String normaliserNumero(String numero) {
        String cleaned = numero.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("0")) {
            cleaned = "+33" + cleaned.substring(1);
        }
        return cleaned;
    }
    private void showContactInviteDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_invitation_contacts, null);

        EditText searchField = dialogView.findViewById(R.id.edit_recherche_contact);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recycler_contacts);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Trier les contacts : avec app d'abord
        List<Contact> sortedContacts = new ArrayList<>(contactsList);
        sortedContacts.sort((a, b) -> Boolean.compare(!b.isHasApp(), !a.isHasApp()));

        ContactAdapter contactAdapter = new ContactAdapter(sortedContacts);
        recyclerView.setAdapter(contactAdapter);

        searchField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                contactAdapter.getFilter().filter(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        builder.setTitle("Inviter des contacts");

        builder.setPositiveButton("Inviter", (dialog, which) -> {
            List<Contact> selection = contactAdapter.getSelectedContacts();
            for (Contact contact : selection) {
                envoyerInvitationParSms(contact);
            }
        });

        builder.setNegativeButton("Annuler", null);
        builder.show();
    }
    private void envoyerInvitationParSms(Contact contact) {
        String phone = contact.getPhone();

        // 🔐 Vérifie la permission d'envoi de SMS
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            contactEnCoursPourSms = contact;
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }

        // 🔗 Crée le lien d'invitation raccourci via Firebase Dynamic Links
        FirebaseDynamicLinks.getInstance()
                .createDynamicLink()
                .setLink(Uri.parse("https://collabasket.page.link/invite?groupId=" + groupId))
                .setDomainUriPrefix("https://collabasket.page.link")
                .setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
                .buildShortDynamicLink()
                .addOnSuccessListener(shortLink -> {
                    String message = "Salut ! Rejoins notre groupe de courses " + groupName +
                            " sur Collabasket. Voici le lien : " + shortLink.getShortLink();

                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(phone, null, message, null, null);
                        Toast.makeText(getContext(), "Invitation envoyée à " + phone, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("SMS_INVITE", "Erreur envoi SMS : ", e);
                        Toast.makeText(getContext(), "Erreur lors de l'envoi à " + phone, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DYNAMIC_LINK", "Erreur lors de la création du lien : ", e);
                    Toast.makeText(getContext(), "Échec de la génération du lien", Toast.LENGTH_SHORT).show();
                });
    }
    private void quitterGroupe() {
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> members = (Map<String, Object>) snapshot.get("members");
                    List<String> memberIds = (List<String>) snapshot.get("memberIds");

                    String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    final String[] currentRole = { "Membre" };

                    if (members != null && members.containsKey(currentUid)) {
                        Map<String, Object> currentMember = (Map<String, Object>) members.get(currentUid);
                        if (currentMember.get("role") != null) {
                            currentRole[0] = currentMember.get("role").toString();
                        }
                    }

                    String message = currentRole[0].equals("Propriétaire")
                            ? "Vous êtes le Propriétaire. En quittant, le groupe sera supprimé pour tous. Confirmez-vous ?"
                            : "Souhaitez-vous vraiment quitter ce groupe ?";

                    new AlertDialog.Builder(getContext())
                            .setTitle("Quitter le groupe")
                            .setMessage(message)
                            .setPositiveButton("Oui", (dialog, which) -> {
                                if (currentRole[0].equals("Propriétaire")) {
                                    FirebaseFirestore.getInstance()
                                            .collection("groups")
                                            .document(groupId)
                                            .delete()
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(getContext(), "Groupe supprimé", Toast.LENGTH_SHORT).show();
                                                requireActivity().getSupportFragmentManager()
                                                        .beginTransaction()
                                                        .replace(R.id.fragment_container, new GroupesFragment())
                                                        .commit();
                                            });
                                } else {
                                    if (members != null) {
                                        members.remove(currentUid);
                                    }
                                    if (memberIds != null) {
                                        memberIds.remove(currentUid);
                                    }

                                    FirebaseFirestore.getInstance()
                                            .collection("groups")
                                            .document(groupId)
                                            .update("members", members, "memberIds", memberIds)
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(getContext(), "Vous avez quitté le groupe", Toast.LENGTH_SHORT).show();
                                                requireActivity().getSupportFragmentManager()
                                                        .beginTransaction()
                                                        .replace(R.id.fragment_container, new GroupesFragment())
                                                        .commit();
                                            });
                                }
                            })
                            .setNegativeButton("Annuler", null)
                            .show();
                });
    }
    private void showAddProduitDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ajout_produit_groupes, null);

        EditText editNom = dialogView.findViewById(R.id.edit_nom);
        EditText editQuantite = dialogView.findViewById(R.id.edit_quantite);
        Spinner spinnerUnite = dialogView.findViewById(R.id.spinner_unite);
        Spinner spinnerCategorie = dialogView.findViewById(R.id.spinner_categorie);

        spinnerUnite.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, unitesDisponibles));

        spinnerCategorie.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, categoriesDisponibles));

        new AlertDialog.Builder(getContext())
                .setTitle("Ajouter un produit")
                .setView(dialogView)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    String nom = editNom.getText().toString().trim();
                    String qteText = editQuantite.getText().toString().trim();
                    String unite = spinnerUnite.getSelectedItem().toString();
                    String categorie = spinnerCategorie.getSelectedItem().toString();

                    float quantite;
                    try {
                        quantite = Float.parseFloat(qteText);
                    } catch (NumberFormatException e) {
                        quantite = 1f;
                    }

                    if (!nom.isEmpty()) {
                        String ajoutePar = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        ProduitGroupes produit = new ProduitGroupes(nom, categorie, quantite, unite, groupId, ajoutePar);

                        firestore.collection("groups")
                                .document(groupId)
                                .collection("produits")
                                .add(produit);
                    } else {
                        Toast.makeText(getContext(), "Le nom du produit est requis", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void chargerProduitsEnTempsReel() {
        firestore.collection("groups")
                .document(groupId)
                .collection("produits")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        Toast.makeText(getContext(), "Erreur de synchronisation", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<ProduitGroupesAdapter.ProduitAvecId> produits = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ProduitGroupes produit = doc.toObject(ProduitGroupes.class);
                        produits.add(new ProduitGroupesAdapter.ProduitAvecId(doc.getId(), produit));
                    }

                    produitAdapter.setProduits(produits);
                    textEmptyList.setVisibility(produits.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

}
