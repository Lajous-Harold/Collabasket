package com.example.collabasket.ui.fragments;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Contact;
import com.example.collabasket.model.ProduitGroupes;
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

        if (getArguments() != null) {
            groupId = getArguments().getString("groupId");
            groupName = getArguments().getString("groupName");
        }

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

        TextView titre = rootView.findViewById(R.id.text_group_title);
        titre.setText(groupName);

        firestore = FirebaseFirestore.getInstance();

        recyclerView = rootView.findViewById(R.id.recycler_produits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        produitAdapter = new ProduitGroupesAdapter(groupId);
        recyclerView.setAdapter(produitAdapter);

        loadProduitsForGroup();

        FloatingActionButton fab = rootView.findViewById(R.id.fab_ajouter_groupe);
        fab.setOnClickListener(v -> showAddProduitDialog());

        ImageButton menuButton = rootView.findViewById(R.id.button_menu_options);
        menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.menu_groupe, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.menu_ajouter_membre) {
                    checkContactsPermissionEtCharger();
                    return true;
                } else if (id == R.id.menu_info_groupe) {
                    InfoGroupeFragment infoGroupeFragment = new InfoGroupeFragment();
                    Bundle args = new Bundle();
                    args.putString("groupId", groupId);
                    infoGroupeFragment.setArguments(args);
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, infoGroupeFragment)
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

        return rootView;
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

        SearchView searchView = dialogView.findViewById(R.id.search_contacts);
        ListView listView = dialogView.findViewById(R.id.list_contacts);

        // Tous les contacts triés : avec app d’abord
        List<Contact> sortedContacts = new ArrayList<>(contactsList);
        sortedContacts.sort((a, b) -> Boolean.compare(!b.isHasApp(), !a.isHasApp()));

        // Liste visible et liste filtrée synchronisées
        List<Contact> filteredContacts = new ArrayList<>(sortedContacts);
        List<String> affichages = new ArrayList<>();
        for (Contact c : filteredContacts) {
            String badge = c.isHasApp() ? " ✅" : "";
            affichages.add(c.getName() + " - " + c.getPhone() + badge);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, affichages);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                affichages.clear();
                filteredContacts.clear();

                for (Contact c : sortedContacts) {
                    String phone = c.getPhone().replaceAll("\\s", "");
                    String name = c.getName().toLowerCase();
                    String query = newText.toLowerCase().replaceAll("\\s", "");

                    if (name.contains(query) || phone.contains(query)) {
                        filteredContacts.add(c);
                        String badge = c.isHasApp() ? " ✅" : "";
                        affichages.add(c.getName() + " - " + c.getPhone() + badge);
                    }
                }

                adapter.clear();
                adapter.addAll(affichages);
                adapter.notifyDataSetChanged();
                return true;
            }
        });

        new AlertDialog.Builder(getContext())
                .setTitle("Sélectionner des contacts à inviter")
                .setView(dialogView)
                .setPositiveButton("Inviter", (dialog, which) -> {
                    SparseBooleanArray checked = listView.getCheckedItemPositions();
                    for (int i = 0; i < checked.size(); i++) {
                        if (checked.valueAt(i)) {
                            int position = checked.keyAt(i);
                            Contact selected = filteredContacts.get(position);
                            selected.setSelected(true);
                            sendInvitation(selected, groupId);
                        }
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }



    private void sendInvitation(Contact contact, String groupId) {
        String link = FirebaseDynamicLinks.getInstance()
                .createDynamicLink()
                .setLink(Uri.parse("https://example.com/invite?groupId=" + groupId))
                .setDomainUriPrefix("https://collabasket.page.link")
                .setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
                .buildDynamicLink()
                .getUri()
                .toString();

        String message = "Rejoignez notre groupe sur Collabasket : " + link;

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + contact.getPhone()));
        intent.putExtra("sms_body", message);
        startActivity(intent);
    }

    private void loadProduitsForGroup() {
        firestore.collection("groups")
                .document(groupId)
                .collection("produits")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ProduitGroupesAdapter.ProduitAvecId> produits = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ProduitGroupes produit = doc.toObject(ProduitGroupes.class);
                        produits.add(new ProduitGroupesAdapter.ProduitAvecId(doc.getId(), produit));
                    }
                    produitAdapter.setProduits(produits);
                });
    }
    private void quitterGroupe() {
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Map<String, Object>> members = (List<Map<String, Object>>) snapshot.get("members");
                    List<String> memberIds = (List<String>) snapshot.get("memberIds");

                    String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    final String[] currentRole = { "Membre" };

                    for (Map<String, Object> membre : members) {
                        if (currentUid.equals(membre.get("userId"))) {
                            currentRole[0] = membre.get("role") != null ? membre.get("role").toString() : "Membre";
                            break;
                        }
                    }

                    new AlertDialog.Builder(getContext())
                            .setTitle("Quitter le groupe")
                            .setMessage(currentRole[0].equals("Propriétaire")
                                    ? "Vous êtes le Propriétaire. En quittant, le groupe sera supprimé pour tous. Confirmez-vous ?"
                                    : "Souhaitez-vous vraiment quitter ce groupe ?")
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
                                    members.removeIf(m -> currentUid.equals(m.get("userId")));
                                    memberIds.removeIf(id1 -> id1.equals(currentUid));

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
                        final float finalQuantite = quantite;
                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        firestore.collection("users")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(doc -> {
                                    String ajoutePar = doc.getString("username");
                                    if (ajoutePar == null || ajoutePar.isEmpty()) {
                                        ajoutePar = "Inconnu";
                                    }

                                    ProduitGroupes produit = new ProduitGroupes(nom, categorie, finalQuantite, unite, groupId, ajoutePar);

                                    firestore.collection("groups")
                                            .document(groupId)
                                            .collection("produits")
                                            .add(produit)
                                            .addOnSuccessListener(ref -> loadProduitsForGroup());
                                });
                    } else {
                        Toast.makeText(getContext(), "Le nom du produit est requis", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}
